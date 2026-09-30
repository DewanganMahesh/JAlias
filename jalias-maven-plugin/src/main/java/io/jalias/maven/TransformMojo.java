package io.jalias.maven;

import io.jalias.JAlias;
import io.jalias.compiler.CompilationResult;
import io.jalias.compiler.SourceTreeTransformer;
import io.jalias.exceptions.JAliasException;
import io.jalias.model.AliasOptions;
import io.jalias.model.SourceUnit;
import io.jalias.parser.AliasImportParser;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Transforms sources that use JAlias import aliases into ordinary Java, so that the rest of the build
 * can compile them with plain {@code javac}.
 *
 * <p>The files that use alias syntax must live in a directory that is <em>not</em> compiled directly,
 * {@code src/main/jalias} by default. This goal runs in {@code generate-sources}, writes the generated
 * Java to {@code target/generated-sources/jalias} and registers that directory as a compile source root,
 * so {@code mvn compile}, {@code mvn package} and the Spring Boot repackage goal work without further
 * configuration. Without this step {@code javac} - and therefore Maven, Gradle and the IDE - would report
 * {@code ';' expected} on the alias lines, because the aliasing syntax is JAlias' input, not Java's.
 *
 * <p>Parameters (all also settable on the command line):
 *
 * <ul>
 *   <li>{@code jalias.sourceDirectory} - default {@code ${project.basedir}/src/main/jalias}</li>
 *   <li>{@code jalias.outputDirectory} - default
 *       {@code ${project.build.directory}/generated-sources/jalias}</li>
 *   <li>{@code jalias.addSourceRoot} - default {@code true}</li>
 *   <li>{@code jalias.emitAliasComments} - default {@code true}</li>
 *   <li>{@code jalias.verify} - default {@code false}, compiles the generated sources to fail early</li>
 *   <li>{@code jalias.skip} - default {@code false}</li>
 * </ul>
 */
public class TransformMojo extends AbstractMojo {

    /** Cheap prefilter so that only plausible files are parsed while scanning compiled roots. */
    private static final Pattern ALIAS_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?[\\w.$*]+\\s+as\\s+\\w+\\s*;");

    private MavenProject project;
    private File sourceDirectory;
    private File outputDirectory;
    private boolean addSourceRoot = true;
    private boolean emitAliasComments = true;
    private boolean verify;
    private List<String> classpathElements;
    private boolean skip;

    /**
     * Runs the transformation.
     *
     * @throws MojoExecutionException if the sources cannot be transformed or verified
     * @throws MojoFailureException   if the source directory overlaps a compiled source root
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("JAlias transformation skipped (-Djalias.skip=true)");
            return;
        }
        if (sourceDirectory == null) {
            throw new MojoExecutionException("JAlias: sourceDirectory is not configured");
        }
        Path sourceRoot = sourceDirectory.toPath();
        if (!sourceRoot.toFile().isDirectory()) {
            getLog().info("JAlias: no alias sources in " + sourceDirectory + ", nothing to do");
            return;
        }
        rejectOverlappingSourceRoots(sourceRoot);
        rejectAliasSourcesInCompiledRoots();

        Path outputRoot = outputDirectory.toPath();
        SourceTreeTransformer.Result result;
        try {
            result = new SourceTreeTransformer(aliasOptions()).transform(sourceRoot, outputRoot);
        } catch (IOException e) {
            throw new MojoExecutionException("JAlias could not transform " + sourceRoot + ": " + e.getMessage(), e);
        }

        if (addSourceRoot && project != null) {
            project.addCompileSourceRoot(outputRoot.toString());
        }
        getLog().info("JAlias " + result.summary());
        for (SourceTreeTransformer.TransformedFile file : result.withAliases()) {
            getLog().info("  " + file.source().getFileName() + " -> " + file.aliases()
                    + " (" + file.replacements() + " reference(s) rewritten)");
        }
        if (verify) {
            verify(result);
        }
    }

    private AliasOptions aliasOptions() {
        return AliasOptions.defaults().withEmitAliasComments(emitAliasComments);
    }

    /**
     * The alias directory must not be compiled directly, because {@code javac} cannot parse alias syntax.
     * Failing fast with a clear message beats a confusing {@code ';' expected} error from the compiler.
     */
    private void rejectOverlappingSourceRoots(Path sourceRoot) throws MojoFailureException {
        Path normalizedSourceRoot = sourceRoot.toAbsolutePath().normalize();
        for (String root : compiledSourceRoots()) {
            Path compileRoot = Path.of(root).toAbsolutePath().normalize();
            if (normalizedSourceRoot.startsWith(compileRoot)) {
                throw new MojoFailureException("JAlias: the alias source directory " + sourceRoot
                        + " is inside the compiled source root " + compileRoot + ". Keep the files that use"
                        + " alias syntax in a directory that is not compiled directly (src/main/jalias by"
                        + " default, or <sourceDirectory>), because javac cannot parse 'import x as y;'.");
            }
        }
    }

    /**
     * Detects the most common misconfiguration: a file that uses alias syntax sitting in a directory that
     * {@code javac} compiles directly, where the only thing the build reports is the cryptic
     * {@code ';' expected} on the {@code as} line. Scanning the compiled roots lets JAlias explain the
     * problem instead.
     */
    private void rejectAliasSourcesInCompiledRoots() throws MojoExecutionException, MojoFailureException {
        for (String root : compiledSourceRoots()) {
            Path directory = Path.of(root);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            List<Path> files;
            try {
                files = SourceTreeTransformer.javaFiles(directory);
            } catch (IOException e) {
                throw new MojoExecutionException("JAlias: cannot scan " + directory + ": " + e.getMessage(), e);
            }
            for (Path file : files) {
                if (usesAliasSyntax(file)) {
                    throw new MojoFailureException("JAlias: " + file + " uses alias syntax but is inside the"
                            + " compiled source root " + directory + ", so javac sees it and reports"
                            + " \"';' expected\" on the 'as' line. Move the file to " + sourceDirectory
                            + " (or configure <sourceDirectory>) so that JAlias transforms it before the"
                            + " compiler runs.");
                }
            }
        }
    }

    private boolean usesAliasSyntax(Path file) throws MojoExecutionException {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("JAlias cannot read " + file + ": " + e.getMessage(), e);
        }
        if (!ALIAS_IMPORT.matcher(text).find()) {
            return false;
        }
        try {
            return !new AliasImportParser().parse(SourceUnit.fromPath(file)).aliases().isEmpty();
        } catch (IOException | JAliasException e) {
            getLog().debug("JAlias: ignoring " + file + " while scanning compiled roots: " + e.getMessage());
            return false;
        }
    }

    private List<String> compiledSourceRoots() {
        if (project == null) {
            return List.of();
        }
        List<String> roots = new ArrayList<>();
        if (project.getCompileSourceRoots() != null) {
            roots.addAll(project.getCompileSourceRoots());
        }
        if (project.getTestCompileSourceRoots() != null) {
            roots.addAll(project.getTestCompileSourceRoots());
        }
        return roots.stream().filter(root -> root != null && !root.isBlank()).toList();
    }

    /**
     * Compiles the generated sources with the project classpath, so that errors surface during
     * {@code generate-sources} instead of later.
     */
    private void verify(SourceTreeTransformer.Result result) throws MojoExecutionException {
        List<SourceUnit> units = new ArrayList<>();
        try {
            for (SourceTreeTransformer.TransformedFile file : result.files()) {
                units.add(SourceUnit.fromPath(file.target()));
            }
        } catch (IOException e) {
            throw new MojoExecutionException("JAlias cannot read the generated sources: " + e.getMessage(), e);
        }
        List<String> classpath = classpathElements;
        if (classpath == null && project != null) {
            try {
                classpath = new ArrayList<>(project.getCompileClasspathElements());
            } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
                getLog().debug("JAlias: compile classpath is not available for verification: " + e.getMessage());
                classpath = List.of();
            }
        }
        if (classpath == null) {
            classpath = List.of();
        }
        CompilationResult compilation = JAlias.compile(units, null, classpath);
        Set<String> problems = new LinkedHashSet<>(compilation.problems());
        if (!problems.isEmpty()) {
            throw new MojoExecutionException("JAlias verification failed:\n  " + String.join("\n  ", problems));
        }
        getLog().info("JAlias verified " + units.size() + " generated source file(s)");
    }

    /**
     * @return the configured alias source directory
     */
    File sourceDirectory() {
        return sourceDirectory;
    }

    /**
     * @param directory alias source directory
     */
    void setSourceDirectory(File directory) {
        this.sourceDirectory = directory;
    }

    /**
     * @return the configured output directory
     */
    File outputDirectory() {
        return outputDirectory;
    }

    /**
     * @param directory output directory
     */
    void setOutputDirectory(File directory) {
        this.outputDirectory = directory;
    }

    /**
     * @param verify whether the generated sources are compiled as well
     */
    void setVerify(boolean verify) {
        this.verify = verify;
    }

    /**
     * @param addSourceRoot whether the output directory is registered as a compile source root
     */
    void setAddSourceRoot(boolean addSourceRoot) {
        this.addSourceRoot = addSourceRoot;
    }

    /**
     * @param project the Maven project
     */
    void setProject(MavenProject project) {
        this.project = project;
    }

    /**
     * @param classpathElements the compile classpath
     */
    void setClasspathElements(List<String> classpathElements) {
        this.classpathElements = classpathElements;
    }

    /**
     * @param emitAliasComments whether the aliased imports are kept as comments
     */
    void setEmitAliasComments(boolean emitAliasComments) {
        this.emitAliasComments = emitAliasComments;
    }

    /**
     * @param skip whether the transformation is skipped
     */
    void setSkip(boolean skip) {
        this.skip = skip;
    }
}
