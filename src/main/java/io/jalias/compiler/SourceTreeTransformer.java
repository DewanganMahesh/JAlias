package io.jalias.compiler;

import io.jalias.model.AliasOptions;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.SourceUnit;
import io.jalias.parser.AliasImportParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Transforms a whole directory tree of alias sources into ordinary Java.
 *
 * <p>This is the piece a build integration needs: a project keeps the files that use alias syntax in a
 * directory that is <em>not</em> compiled directly (for example {@code src/main/jalias}), this class
 * turns them into valid Java under a generated sources directory, and the build tool then compiles that
 * directory. The alias files themselves never reach {@code javac}, which is what makes the syntax usable
 * from Maven, Gradle or any other build.
 *
 * <p>Files are copied one to one, so the generated tree mirrors the layout of the source tree and
 * package declarations stay correct. Files without aliases are copied unchanged.
 */
public final class SourceTreeTransformer {

    /**
     * One transformed file.
     *
     * @param source       the file that was read
     * @param target       the file that was written
     * @param aliases      alias name to qualified class name, in declaration order
     * @param replacements how many alias references were rewritten
     */
    public record TransformedFile(Path source, Path target, Map<String, String> aliases, int replacements) {

        /**
         * @return {@code true} if the file declared at least one alias
         */
        public boolean hasAliases() {
            return !aliases.isEmpty();
        }
    }

    /**
     * The outcome of transforming a source tree.
     *
     * @param sourceRoot the directory that was transformed
     * @param outputRoot the directory the generated sources were written to
     * @param files      one entry per source file
     */
    public record Result(Path sourceRoot, Path outputRoot, List<TransformedFile> files) {

        /**
         * @return the number of files that were written
         */
        public int count() {
            return files.size();
        }

        /**
         * @return only the files that declared aliases
         */
        public List<TransformedFile> withAliases() {
            return files.stream().filter(TransformedFile::hasAliases).toList();
        }

        /**
         * @return every alias of the tree, alias name to qualified class name
         */
        public Map<String, String> aliasMappings() {
            Map<String, String> mappings = new LinkedHashMap<>();
            for (TransformedFile file : files) {
                file.aliases().forEach(mappings::putIfAbsent);
            }
            return mappings;
        }

        /**
         * @return a one line summary, handy for build logs
         */
        public String summary() {
            return "transformed " + count() + " file(s), " + withAliases().size() + " of them using aliases "
                    + aliasMappings() + " -> " + outputRoot;
        }
    }

    private final AliasOptions options;

    /**
     * Creates a transformer with default options.
     */
    public SourceTreeTransformer() {
        this(AliasOptions.defaults());
    }

    /**
     * Creates a transformer.
     *
     * @param options effective options
     */
    public SourceTreeTransformer(AliasOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Transforms every {@code .java} file below {@code sourceRoot} into {@code outputRoot}, keeping the
     * relative layout.
     *
     * @param sourceRoot directory holding the sources, which may use alias syntax
     * @param outputRoot directory the generated Java is written to, created when missing
     * @return what was transformed
     * @throws IOException if a file cannot be read or written
     */
    public Result transform(Path sourceRoot, Path outputRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("The alias source directory does not exist: " + sourceRoot);
        }
        Files.createDirectories(outputRoot);

        AliasImportParser parser = new AliasImportParser();
        AliasSourceTransformer transformer = new AliasSourceTransformer(options);
        List<TransformedFile> files = new ArrayList<>();

        for (Path source : javaFiles(sourceRoot)) {
            SourceUnit unit = SourceUnit.fromPath(source);
            CompilationUnitModel model = parser.parse(unit);
            TransformResult transformed = transformer.transform(model);

            Path target = outputRoot.resolve(sourceRoot.relativize(source).toString());
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, transformed.transformedSource(), StandardCharsets.UTF_8);

            files.add(new TransformedFile(source, target, transformed.aliasMappings(), transformed.replacements()));
        }
        return new Result(sourceRoot.toAbsolutePath().normalize(), outputRoot.toAbsolutePath().normalize(), files);
    }

    /**
     * @param root directory to scan
     * @return every {@code .java} file below the directory, sorted by path
     * @throws IOException if the tree cannot be walked
     */
    public static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }
}
