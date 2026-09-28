package io.jalias.compiler;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.jalias.exceptions.AliasCompilationException;
import io.jalias.exceptions.AliasConflictException;
import io.jalias.model.AliasOptions;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.ImportAlias;
import io.jalias.model.LineIndex;
import io.jalias.model.SourceMap;
import io.jalias.model.SourceUnit;
import io.jalias.resolver.AliasResolver;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rewrites alias syntax into ordinary Java.
 *
 * <p>The transformation is deliberately not a hand written Java parser. It asks the JDK's own
 * compiler to parse the normalised source (aliased imports replaced by comments), then walks the
 * resulting syntax tree with {@link TreePathScanner} and replaces every identifier that refers to an
 * alias with the fully qualified name of the real class.
 *
 * <p>Because <em>every</em> type usage in Java - field and parameter types, return types, generics,
 * arrays, casts, {@code instanceof} patterns, annotations, {@code extends}/{@code implements}/
 * {@code permits} clauses, nested types, static member access, class literals, method references and
 * constructor calls - is spelled as an identifier in that tree, one rule covers the whole language
 * and keeps working for future syntax. Only two positions are treated specially:
 *
 * <ul>
 *   <li>the member name of a qualified access is never rewritten, so {@code Map.Entry} stays intact
 *       even when {@code Entry} is an alias;</li>
 *   <li>an alias used as a method name or as a switch {@code case} label is reported as a conflict,
 *       because JAlias cannot tell which of the two meanings was intended.</li>
 * </ul>
 *
 * <p>Offsets are patched into the text the developer wrote (mapped through {@link SourceMap}), so
 * string literals, comments and Javadoc are untouched.
 */
public final class AliasSourceTransformer {

    private record Patch(int start, int end, String alias, String qualifiedName) {
    }

    private record ParsedUnit(JavacTask task, CompilationUnitTree tree) {
    }

    private final AliasOptions options;

    /**
     * Creates a transformer with default options.
     */
    public AliasSourceTransformer() {
        this(AliasOptions.defaults());
    }

    /**
     * Creates a transformer.
     *
     * @param options effective options
     */
    public AliasSourceTransformer(AliasOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * @return the options in use
     */
    public AliasOptions options() {
        return options;
    }

    /**
     * Transforms a unit using the aliases declared by that unit.
     *
     * @param unit parsed compilation unit
     * @return the transformation result
     */
    public TransformResult transform(CompilationUnitModel unit) {
        return transform(unit, AliasResolver.of(unit.aliases()));
    }

    /**
     * Transforms a unit.
     *
     * @param unit     parsed compilation unit
     * @param resolver aliases that are in scope for the unit
     * @return the transformation result
     * @throws AliasCompilationException if the normalised source cannot be parsed
     * @throws AliasConflictException    if an alias collides with a declared name or is used as a
     *                                   method name or case label
     */
    public TransformResult transform(CompilationUnitModel unit, AliasResolver resolver) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(resolver, "resolver");
        SourceUnit source = unit.source();
        if (unit.aliases().isEmpty()) {
            return new TransformResult(source, source.sourceText(), source.sourceText(), unit.aliasMappings(), 0);
        }

        ParsedUnit parsed = parseNormalized(unit);
        SourcePositions positions = Trees.instance(parsed.task()).getSourcePositions();
        LineIndex lines = unit.normalizedLineIndex();
        String fileName = source.displayName();
        Set<String> aliasNames = unit.aliasNames();

        AliasDeclarationConflictValidator.validate(parsed.tree(), positions, lines, aliasNames, fileName, options);

        List<Patch> patches = new AliasIdentifierScanner(parsed.tree(), positions, lines, aliasNames, resolver,
                fileName).collect();
        String rewritten = applyPatches(source.sourceText(), unit.sourceMap(), patches);
        String transformed = renderAliasLines(rewritten, unit);
        return new TransformResult(source, source.sourceText(), transformed, unit.aliasMappings(), patches.size());
    }

    private ParsedUnit parseNormalized(CompilationUnitModel unit) {
        JavaCompiler compiler = JavacSupport.systemCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        String displayName = unit.source().displayName();
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null,
                List.of(JavacSupport.sourceFile(unit.source().binaryName(), unit.sourceMap().normalizedText())));
        CompilationUnitTree tree;
        try {
            Iterator<? extends CompilationUnitTree> trees = task.parse().iterator();
            tree = trees.hasNext() ? trees.next() : null;
        } catch (IOException e) {
            throw new AliasCompilationException("Cannot parse " + displayName + ": " + e.getMessage(), e);
        }
        List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(JavacSupport::format)
                .toList();
        if (!errors.isEmpty()) {
            throw new AliasCompilationException("Cannot parse " + displayName + ": " + String.join("; ", errors));
        }
        if (tree == null) {
            throw new AliasCompilationException("Cannot parse " + displayName + ": no compilation unit produced");
        }
        return new ParsedUnit(task, tree);
    }

    /**
     * Collects the offsets of all identifiers that stand for an alias.
     */
    private final class AliasIdentifierScanner extends TreePathScanner<Void, Void> {

        private final CompilationUnitTree tree;
        private final SourcePositions positions;
        private final LineIndex lines;
        private final Set<String> aliasNames;
        private final AliasResolver resolver;
        private final String fileName;
        private final List<Patch> patches = new ArrayList<>();

        AliasIdentifierScanner(CompilationUnitTree tree, SourcePositions positions, LineIndex lines,
                               Set<String> aliasNames, AliasResolver resolver, String fileName) {
            this.tree = tree;
            this.positions = positions;
            this.lines = lines;
            this.aliasNames = aliasNames;
            this.resolver = resolver;
            this.fileName = fileName;
        }

        List<Patch> collect() {
            scan(tree, null);
            patches.sort(Comparator.comparingInt(Patch::start));
            return patches;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            String name = node.getName().toString();
            if (aliasNames.contains(name)) {
                rewrite(node, name);
            }
            return super.visitIdentifier(node, unused);
        }

        private void rewrite(IdentifierTree node, String name) {
            TreePath parentPath = getCurrentPath().getParentPath();
            Tree parent = parentPath == null ? null : parentPath.getLeaf();
            if (parent instanceof MemberSelectTree select && select.getIdentifier() == node) {
                return;
            }
            if (parent instanceof MethodInvocationTree invocation && invocation.getMethodSelect() == node) {
                throw new AliasConflictException("Alias '" + name + "' is used as a method name at line "
                        + lineOf(node) + " in " + fileName
                        + ": an alias names a type, so it cannot also name a method");
            }
            if (parent instanceof CaseTree
                    || (parent != null && parent.getKind() == Tree.Kind.CONSTANT_CASE_LABEL)) {
                throw new AliasConflictException("Alias '" + name + "' is used as a case label at line "
                        + lineOf(node) + " in " + fileName
                        + ": qualify the constant or use a different alias name");
            }
            ImportAlias alias = resolver.require(name);
            long start = positions.getStartPosition(tree, node);
            long end = positions.getEndPosition(tree, node);
            if (start < 0 || end < 0) {
                return;
            }
            patches.add(new Patch((int) start, (int) end, name, alias.qualifiedName()));
        }

        private int lineOf(Tree node) {
            long offset = positions.getStartPosition(tree, node);
            return offset < 0 ? 0 : lines.lineNumber((int) offset);
        }
    }

    /**
     * Replaces the given ranges of the original source with fully qualified names. Ranges are mapped
     * from normalised offsets back to the original text.
     */
    private String applyPatches(String original, SourceMap sourceMap, List<Patch> patches) {
        if (patches.isEmpty()) {
            return original;
        }
        StringBuilder result = new StringBuilder(original.length() + 32);
        int cursor = 0;
        for (Patch patch : patches) {
            int start = sourceMap.toOriginalOffset(patch.start());
            int end = sourceMap.toOriginalOffset(patch.end());
            if (start < cursor || end > original.length() || start > end) {
                continue;
            }
            result.append(original, cursor, start).append(patch.qualifiedName());
            cursor = end;
        }
        result.append(original, cursor, original.length());
        return result.toString();
    }

    /**
     * Rewrites the aliased import statements in the generated source. The statement is not valid Java,
     * so it is always replaced: by a comment showing the original declaration when
     * {@link AliasOptions#emitAliasComments()} is enabled, by nothing otherwise. Line numbers are
     * preserved either way.
     */
    private String renderAliasLines(String text, CompilationUnitModel unit) {
        if (unit.aliasLineSpans().isEmpty()) {
            return text;
        }
        LineIndex lines = new LineIndex(text);
        List<String> contents = lines.copyContents();
        for (CompilationUnitModel.LineSpan span : unit.aliasLineSpans()) {
            if (span.startLine() >= contents.size()) {
                continue;
            }
            String indent = leadingWhitespace(contents.get(span.startLine()));
            String replacement = options.emitAliasComments()
                    ? indent + "// " + span.alias().declaration()
                    : "";
            contents.set(span.startLine(), replacement);
            for (int line = span.startLine() + 1; line <= span.endLine() && line < contents.size(); line++) {
                contents.set(line, "");
            }
        }
        return lines.reassemble(contents);
    }

    private static String leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(0, index);
    }
}
