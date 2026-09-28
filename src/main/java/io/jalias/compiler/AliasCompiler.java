package io.jalias.compiler;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.jalias.exceptions.AliasCompilationException;
import io.jalias.exceptions.AliasConflictException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.imports.ImportResolver;
import io.jalias.model.AliasOptions;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.ImportAlias;
import io.jalias.model.SourceUnit;
import io.jalias.model.TypeNames;
import io.jalias.parser.AliasImportParser;
import io.jalias.resolver.AliasRegistry;
import io.jalias.resolver.AliasResolver;

import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The public entry point of the compiler side of JAlias.
 *
 * <p>A compilation run has four steps:
 *
 * <ol>
 *   <li>parse every unit and collect the aliases, rejecting duplicates, circular targets and
 *       collisions with plain imports;</li>
 *   <li>check the aliases against the compiler's symbol table: does the imported class exist, and
 *       does the alias clash with a type that is already visible;</li>
 *   <li>rewrite the sources: aliases become fully qualified names, nothing else changes;</li>
 *   <li>compile the generated Java and hand back the class files.</li>
 * </ol>
 *
 * <p>Alias level problems throw a subclass of {@link io.jalias.exceptions.JAliasException}; problems
 * in the developer's own code come back as diagnostics in the {@link CompilationResult}.
 */
public final class AliasCompiler {

    private final AliasOptions options;
    private final AliasImportParser parser = new AliasImportParser();
    private final ImportResolver importResolver = new ImportResolver();
    private final AliasSourceTransformer transformer;

    /**
     * Creates a compiler with default options.
     */
    public AliasCompiler() {
        this(AliasOptions.defaults());
    }

    /**
     * Creates a compiler.
     *
     * @param options effective options
     */
    public AliasCompiler(AliasOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        this.transformer = new AliasSourceTransformer(options);
    }

    /**
     * @return the options in use
     */
    public AliasOptions options() {
        return options;
    }

    /**
     * @return the parser used to read alias declarations
     */
    public AliasImportParser parser() {
        return parser;
    }

    /**
     * Parses a single unit without compiling anything.
     *
     * @param unit source unit
     * @return the parsed model
     */
    public CompilationUnitModel parse(SourceUnit unit) {
        return parser.parse(unit);
    }

    /**
     * Parses and rewrites a single unit without compiling anything, which is handy for tooling and
     * for looking at the generated Java.
     *
     * @param unit source unit
     * @return the transformation result
     */
    public TransformResult transform(SourceUnit unit) {
        return transformer.transform(parser.parse(unit));
    }

    /**
     * Builds a registry from the aliases declared by the given units, failing on conflicts.
     *
     * @param units units to inspect
     * @return the registry
     */
    public AliasRegistry registry(SourceUnit... units) {
        AliasRegistry registry = new AliasRegistry();
        for (SourceUnit unit : units) {
            registry.registerAll(parser.parse(unit).aliases());
        }
        return registry;
    }

    /**
     * Compiles the sources of the request.
     *
     * @param request what to compile and where to put the result
     * @return the compilation result
     * @throws io.jalias.exceptions.AliasSyntaxException  if an aliased import is malformed
     * @throws AliasConflictException                     if an alias conflicts with something else
     * @throws InvalidImportException                     if an import cannot be resolved
     * @throws AliasCompilationException                  if no compiler is available
     */
    public CompilationResult compile(AliasCompilationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.units().isEmpty()) {
            throw new IllegalArgumentException("A compilation request needs at least one source unit");
        }

        List<CompilationUnitModel> units = new ArrayList<>();
        for (SourceUnit source : request.units()) {
            units.add(parser.parse(source));
        }
        AliasRegistry session = new AliasRegistry();
        for (CompilationUnitModel unit : units) {
            new AliasRegistry().registerAll(unit.aliases());
            importResolver.checkAliasConflicts(unit);
            for (ImportAlias alias : unit.aliases()) {
                session.registerIfAbsent(alias);
            }
        }

        if (options.verifyImportTargets() || options.checkVisibleTypeConflicts()) {
            checkAgainstCompiler(request, units);
        }

        Map<String, String> generatedSources = new LinkedHashMap<>();
        List<SourceUnit> transformedUnits = new ArrayList<>();
        try {
            for (CompilationUnitModel unit : units) {
                TransformResult result = transformer.transform(unit, AliasResolver.of(unit.aliases()));
                generatedSources.put(unit.source().binaryName(), result.transformedSource());
                transformedUnits.add(SourceUnit.of(unit.source().binaryName(), result.transformedSource()));
            }
        } catch (AliasCompilationException failure) {
            return new CompilationResult(false, List.of(), List.of(failure.getMessage()), Map.of(), Map.of(),
                    session.mappings(), units, request.outputDirectory(), request.classpath());
        }
        return compileTransformed(request, units, transformedUnits, generatedSources, session);
    }

    private CompilationResult compileTransformed(AliasCompilationRequest request, List<CompilationUnitModel> units,
                                                 List<SourceUnit> transformedUnits,
                                                 Map<String, String> generatedSources, AliasRegistry session) {
        JavaCompiler compiler = JavacSupport.systemCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> sources = new ArrayList<>();
        for (SourceUnit source : transformedUnits) {
            sources.add(JavacSupport.sourceFile(source.binaryName(), source.sourceText()));
        }
        List<String> javacOptions = JavacSupport.options(request.classpath(), request.outputDirectory(),
                request.javacOptions());

        Map<String, byte[]> classes = Map.of();
        boolean success;
        if (request.outputDirectory() != null) {
            try {
                Files.createDirectories(request.outputDirectory());
            } catch (IOException e) {
                throw new AliasCompilationException("Cannot create output directory "
                        + request.outputDirectory(), e);
            }
            try (StandardJavaFileManager fileManager =
                         compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                JavaCompiler.CompilationTask task =
                        compiler.getTask(null, fileManager, diagnostics, javacOptions, null, sources);
                success = Boolean.TRUE.equals(task.call());
            } catch (IOException e) {
                throw new AliasCompilationException("Compilation failed: " + e.getMessage(), e);
            }
        } else {
            JavacSupport.InMemoryFileManager fileManager;
            try {
                fileManager = new JavacSupport.InMemoryFileManager(compiler, request.classpath());
            } catch (IOException e) {
                throw new AliasCompilationException("Cannot create the compiler file manager: " + e.getMessage(), e);
            }
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, diagnostics, javacOptions, null, sources);
            success = Boolean.TRUE.equals(task.call());
            classes = fileManager.classes();
        }

        return new CompilationResult(success, diagnostics.getDiagnostics(), List.of(), generatedSources, classes,
                session.mappings(), units, request.outputDirectory(), request.classpath());
    }

    /**
     * Uses the compiler's own symbol table to validate aliases: the imported class must exist and the
     * alias must not collide with a type that is already visible.
     *
     * <p>When the sources do not parse, or when no symbol table is available, the checks are skipped
     * silently: the subsequent compilation reports the syntax problem in the usual way.
     */
    private void checkAgainstCompiler(AliasCompilationRequest request, List<CompilationUnitModel> units) {
        JavaCompiler compiler = JavacSupport.systemCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> sources = new ArrayList<>();
        for (CompilationUnitModel unit : units) {
            sources.add(JavacSupport.sourceFile(unit.source().binaryName(), unit.sourceMap().normalizedText()));
        }
        List<String> javacOptions = JavacSupport.options(request.classpath(), null, request.javacOptions());
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, javacOptions, null, sources);
        List<CompilationUnitTree> trees = new ArrayList<>();
        try {
            task.parse().forEach(trees::add);
        } catch (IOException | RuntimeException e) {
            return;
        }
        boolean parseFailed = diagnostics.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
        if (parseFailed) {
            return;
        }
        Elements elements;
        try {
            elements = task.getElements();
        } catch (RuntimeException e) {
            return;
        }
        Set<String> declared = declaredTypeNames(trees);
        for (CompilationUnitModel unit : units) {
            for (ImportAlias alias : unit.aliases()) {
                if (options.verifyImportTargets() && !declared.contains(alias.qualifiedName())
                        && typeElement(elements, alias.qualifiedName()) == null) {
                    throw new InvalidImportException("Cannot resolve import '" + alias.qualifiedName()
                            + "' for alias '" + alias.alias() + "' (" + alias.location()
                            + "): the class is not on the compilation classpath");
                }
                if (options.checkVisibleTypeConflicts()) {
                    checkVisibleConflict(elements, declared, unit, alias);
                }
            }
        }
    }

    private void checkVisibleConflict(Elements elements, Set<String> declared, CompilationUnitModel unit,
                                      ImportAlias alias) {
        Element visible = typeElement(elements, alias.alias());
        if (visible == null && !TypeNames.isQualified(alias.alias())) {
            visible = typeElement(elements, "java.lang." + alias.alias());
        }
        if (visible != null) {
            throw new AliasConflictException("Alias '" + alias.alias() + "' conflicts with existing type '" + visible
                    + "' (" + alias.location() + ")");
        }
        String inPackage = unit.packageName().isEmpty() ? alias.alias()
                : unit.packageName() + "." + alias.alias();
        if (declared.contains(inPackage)) {
            throw new AliasConflictException("Alias '" + alias.alias() + "' conflicts with the type '" + inPackage
                    + "' declared in this compilation (" + alias.location() + ")");
        }
    }

    private static Element typeElement(Elements elements, String name) {
        Element element = lookup(elements, name);
        if (element != null) {
            return element;
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            String owner = name.substring(0, lastDot);
            String simple = name.substring(lastDot + 1);
            if (TypeNames.looksLikeAlias(simple) && TypeNames.looksLikeAlias(TypeNames.simpleNameOf(owner))) {
                // nested type written in canonical form (java.util.AbstractMap.SimpleEntry)
                return lookup(elements, owner + "$" + simple);
            }
        }
        return null;
    }

    private static Element lookup(Elements elements, String name) {
        try {
            return elements.getTypeElement(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Collects the qualified names of every type declared by the given compilation units, nested
     * types included, so that aliases targeting them are accepted without a classpath lookup.
     */
    private static Set<String> declaredTypeNames(List<CompilationUnitTree> trees) {
        Set<String> names = new LinkedHashSet<>();
        for (CompilationUnitTree tree : trees) {
            String packageName = tree.getPackageName() == null ? "" : tree.getPackageName().toString();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree node, Void unused) {
                    Deque<String> enclosing = new ArrayDeque<>();
                    TreePath path = getCurrentPath();
                    while (path != null) {
                        if (path.getLeaf() instanceof ClassTree outer && !outer.getSimpleName().isEmpty()) {
                            enclosing.addFirst(outer.getSimpleName().toString());
                        }
                        path = path.getParentPath();
                    }
                    if (!enclosing.isEmpty()) {
                        String qualified = String.join(".", enclosing);
                        names.add(packageName.isEmpty() ? qualified : packageName + "." + qualified);
                    }
                    return super.visitClass(node, unused);
                }
            }.scan(tree, null);
        }
        return names;
    }
}
