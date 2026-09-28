package io.jalias.compiler;

import io.jalias.model.CompilationUnitModel;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Outcome of a compilation: whether it succeeded, what the compiler said, which Java source was
 * generated, and the classes that were produced.
 *
 * <p>JAlias reports problems <em>inside</em> a developer's code the same way {@code javac} does, as
 * diagnostics, and only throws for alias level errors. {@link #problems()} contains the readable
 * form of everything that went wrong.
 */
public final class CompilationResult {

    private final boolean success;
    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    private final List<String> problems;
    private final Map<String, String> generatedSources;
    private final Map<String, byte[]> classes;
    private final Map<String, String> aliasMappings;
    private final List<CompilationUnitModel> units;
    private final Path outputDirectory;
    private final List<String> classpath;
    private ClassLoader classLoader;

    CompilationResult(boolean success,
                      List<Diagnostic<? extends JavaFileObject>> diagnostics,
                      List<String> problems,
                      Map<String, String> generatedSources,
                      Map<String, byte[]> classes,
                      Map<String, String> aliasMappings,
                      List<CompilationUnitModel> units,
                      Path outputDirectory,
                      List<String> classpath) {
        this.success = success;
        this.diagnostics = List.copyOf(diagnostics);
        this.problems = List.copyOf(problems);
        this.generatedSources = Map.copyOf(generatedSources);
        this.classes = new LinkedHashMap<>(classes);
        this.aliasMappings = Map.copyOf(aliasMappings);
        this.units = List.copyOf(units);
        this.outputDirectory = outputDirectory;
        this.classpath = List.copyOf(classpath);
    }

    /**
     * @return {@code true} when the compiler accepted the generated source
     */
    public boolean success() {
        return success;
    }

    /**
     * @return every diagnostic the compiler produced, warnings and notes included
     */
    public List<Diagnostic<? extends JavaFileObject>> diagnostics() {
        return diagnostics;
    }

    /**
     * @return {@code true} when at least one diagnostic is an error
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
    }

    /**
     * @return the generated Java source, keyed by binary name
     */
    public Map<String, String> generatedSources() {
        return generatedSources;
    }

    /**
     * @return the compiled class files, keyed by binary name; empty when class files were written to
     *         an output directory
     */
    public Map<String, byte[]> classes() {
        return Map.copyOf(classes);
    }

    /**
     * @return alias name to qualified class name for every alias used in this run
     */
    public Map<String, String> aliasMappings() {
        return aliasMappings;
    }

    /**
     * @return the parsed compilation units of this run
     */
    public List<CompilationUnitModel> units() {
        return units;
    }

    /**
     * @return the directory class files were written to, or {@code null}
     */
    public Path outputDirectory() {
        return outputDirectory;
    }

    /**
     * @return a readable list of everything that failed: alias problems plus compiler errors
     */
    public List<String> problems() {
        List<String> all = new ArrayList<>(problems);
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                all.add(JavacSupport.format(diagnostic));
            }
        }
        return all;
    }

    /**
     * @return a readable list of compiler warnings
     */
    public List<String> warnings() {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.WARNING
                        || diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
                .map(JavacSupport::format)
                .toList();
    }

    /**
     * Returns the class loader that defines the classes of this compilation.
     *
     * <p>The same loader instance is returned on every call, and it also knows the classpath this
     * compilation was run with. That matters for correctness: loading one class twice through two
     * different loaders would produce two distinct {@code Class} objects and break {@code instanceof},
     * casts and identity comparisons between them.
     *
     * @return the loader
     */
    public ClassLoader classLoader() {
        synchronized (this) {
            if (classLoader == null) {
                classLoader = createClassLoader();
            }
            return classLoader;
        }
    }

    private ClassLoader createClassLoader() {
        ClassLoader parent = CompilationResult.class.getClassLoader();
        List<URL> urls = new ArrayList<>();
        if (outputDirectory != null) {
            addUrl(urls, outputDirectory);
        }
        for (String entry : classpath) {
            addUrl(urls, Path.of(entry));
        }
        ClassLoader delegate = urls.isEmpty()
                ? parent
                : new URLClassLoader(urls.toArray(URL[]::new), parent);
        if (!classes.isEmpty()) {
            return new AliasClassLoader(classes, delegate);
        }
        return delegate;
    }

    private static void addUrl(List<URL> urls, Path path) {
        try {
            urls.add(path.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Cannot build a class loader entry for " + path, e);
        }
    }

    /**
     * Loads one of the compiled classes.
     *
     * @param binaryName binary name of the class
     * @return the loaded class
     * @throws ClassNotFoundException if the class was not produced by this compilation
     */
    public Class<?> loadClass(String binaryName) throws ClassNotFoundException {
        return classLoader().loadClass(binaryName);
    }

    /**
     * Writes the in-memory classes to a directory.
     *
     * @param directory target directory
     * @throws IOException if a file cannot be written
     */
    public void writeClassesTo(Path directory) throws IOException {
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            Path target = directory.resolve(entry.getKey().replace('.', '/') + ".class");
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
    }

    /**
     * @return a one line summary, handy for logs
     */
    public String summary() {
        String detail = problems().isEmpty() ? "" : ", problems: " + problems();
        return (success ? "compiled " : "failed ") + generatedSources.size() + " unit(s), " + classes.size()
                + " class(es), " + aliasMappings.size() + " alias(es)" + detail;
    }

    @Override
    public String toString() {
        return summary();
    }
}
