package io.jalias;

import io.jalias.compiler.AliasCompilationRequest;
import io.jalias.compiler.AliasCompiler;
import io.jalias.compiler.CompilationResult;
import io.jalias.compiler.TransformResult;
import io.jalias.exceptions.AliasCompilationException;
import io.jalias.model.ImportAlias;
import io.jalias.model.SourceUnit;
import io.jalias.resolver.AliasRegistry;
import io.jalias.resolver.AliasResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Convenience facade for the two things JAlias does: rewriting alias syntax into plain Java and
 * compiling such sources.
 *
 * <pre>{@code
 * SourceUnit unit = SourceUnit.of("Example", """
 *         import java.util.Date as UtilDate;
 *         import java.sql.Date as SqlDate;
 *
 *         class Example {
 *             UtilDate createdAt = new UtilDate();
 *             SqlDate databaseDate = new SqlDate(System.currentTimeMillis());
 *         }
 *         """);
 *
 * CompilationResult result = JAlias.compile(unit);
 * Class<?> example = result.loadClass("Example");
 * }</pre>
 *
 * <p>For finer control use {@link io.jalias.compiler.AliasCompiler} and
 * {@link io.jalias.compiler.AliasCompilationRequest} directly.
 */
public final class JAlias {

    /** Version used when the implementation version is not available from the manifest. */
    public static final String FALLBACK_VERSION = "0.1.0";

    private JAlias() {
    }

    /**
     * Rewrites alias syntax into plain Java, without compiling.
     *
     * @param sourceText Java source that may use {@code import x as y;} syntax
     * @return the transformation result
     */
    public static TransformResult transform(String sourceText) {
        return transform(SourceUnit.of(sourceText));
    }

    /**
     * Rewrites alias syntax into plain Java, without compiling.
     *
     * @param unit the source unit
     * @return the transformation result
     */
    public static TransformResult transform(SourceUnit unit) {
        return new AliasCompiler().transform(unit);
    }

    /**
     * Parses and compiles the given units in memory.
     *
     * @param units sources to compile
     * @return the compilation result
     */
    public static CompilationResult compile(SourceUnit... units) {
        return compile(List.of(units));
    }

    /**
     * Parses and compiles the given units in memory.
     *
     * @param units sources to compile
     * @return the compilation result
     */
    public static CompilationResult compile(Collection<SourceUnit> units) {
        return new AliasCompiler().compile(AliasCompilationRequest.builder().sources(units).build());
    }

    /**
     * Compiles the given units against a classpath, optionally writing class files to disk.
     *
     * @param units           sources to compile
     * @param outputDirectory where class files are written, or {@code null} to keep them in memory
     * @param classpath       classpath entries used to resolve imports
     * @return the compilation result
     */
    public static CompilationResult compile(Collection<SourceUnit> units, Path outputDirectory,
                                           List<String> classpath) {
        AliasCompilationRequest.Builder builder = AliasCompilationRequest.builder().sources(units);
        if (outputDirectory != null) {
            builder.outputDirectory(outputDirectory);
        }
        if (classpath != null) {
            builder.addClasspath(classpath.toArray(String[]::new));
        }
        return new AliasCompiler().compile(builder.build());
    }

    /**
     * Reads and compiles {@code .java} files from disk.
     *
     * @param files source files
     * @return the compilation result
     * @throws AliasCompilationException if a file cannot be read
     */
    public static CompilationResult compileFiles(Path... files) {
        List<SourceUnit> units = new ArrayList<>();
        for (Path file : files) {
            try {
                units.add(SourceUnit.fromPath(file));
            } catch (IOException e) {
                throw new AliasCompilationException("Cannot read " + file + ": " + e.getMessage(), e);
            }
        }
        return compile(units);
    }

    /**
     * Builds a registry from fixed alias declarations, for programmatic use and tests.
     *
     * @param aliases aliases to register
     * @return the registry
     */
    public static AliasRegistry registry(ImportAlias... aliases) {
        return new AliasRegistry().registerAll(List.of(aliases));
    }

    /**
     * @param aliases aliases in scope
     * @return an immutable resolver over the given aliases
     */
    public static AliasResolver resolver(Collection<ImportAlias> aliases) {
        return AliasResolver.of(aliases);
    }

    /**
     * @return the version of the JAlias jar, or {@link #FALLBACK_VERSION} when it is unknown
     */
    public static String version() {
        String version = JAlias.class.getPackage().getImplementationVersion();
        return version == null ? FALLBACK_VERSION : version;
    }
}
