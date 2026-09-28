package io.jalias.compiler;

import io.jalias.model.SourceUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Description of one compilation run: the sources, where to look for classes and where to write
 * class files.
 *
 * <p>Requests are immutable; use {@link #builder()} to assemble one.
 */
public final class AliasCompilationRequest {

    private final List<SourceUnit> units;
    private final List<String> classpath;
    private final Path outputDirectory;
    private final List<String> javacOptions;

    private AliasCompilationRequest(Builder builder) {
        this.units = List.copyOf(builder.units);
        this.classpath = List.copyOf(builder.classpath);
        this.outputDirectory = builder.outputDirectory;
        this.javacOptions = List.copyOf(builder.javacOptions);
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param sources the compilation units to compile
     * @return a request compiling to memory
     */
    public static AliasCompilationRequest of(SourceUnit... sources) {
        return builder().sources(sources).build();
    }

    /**
     * @return the compilation units of this request
     */
    public List<SourceUnit> units() {
        return units;
    }

    /**
     * @return the classpath entries used to resolve imported types
     */
    public List<String> classpath() {
        return classpath;
    }

    /**
     * @return the directory class files are written to, or {@code null} for in-memory compilation
     */
    public Path outputDirectory() {
        return outputDirectory;
    }

    /**
     * @return extra options handed to the Java compiler
     */
    public List<String> javacOptions() {
        return javacOptions;
    }

    /**
     * Builder for {@link AliasCompilationRequest}.
     */
    public static final class Builder {

        private final List<SourceUnit> units = new ArrayList<>();
        private final List<String> classpath = new ArrayList<>();
        private final List<String> javacOptions = new ArrayList<>();
        private Path outputDirectory;

        private Builder() {
        }

        /**
         * Adds compilation units.
         *
         * @param sources sources to add
         * @return this builder
         */
        public Builder sources(SourceUnit... sources) {
            return addSources(List.of(sources));
        }

        /**
         * Adds compilation units.
         *
         * @param sources sources to add
         * @return this builder
         */
        public Builder sources(Collection<SourceUnit> sources) {
            return addSources(sources);
        }

        /**
         * Adds a single compilation unit.
         *
         * @param source source to add
         * @return this builder
         */
        public Builder addSource(SourceUnit source) {
            units.add(Objects.requireNonNull(source, "source"));
            return this;
        }

        private Builder addSources(Collection<SourceUnit> sources) {
            sources.forEach(this::addSource);
            return this;
        }

        /**
         * Sets the classpath entries.
         *
         * @param entries classpath entries (directories or jars)
         * @return this builder
         */
        public Builder classpath(String... entries) {
            return addClasspath(entries);
        }

        /**
         * Sets the classpath entries.
         *
         * @param entries classpath entries (directories or jars)
         * @return this builder
         */
        public Builder addClasspath(String... entries) {
            for (String entry : entries) {
                if (entry != null && !entry.isBlank()) {
                    classpath.add(entry);
                }
            }
            return this;
        }

        /**
         * Sets the classpath entries.
         *
         * @param entries classpath entries (directories or jars)
         * @return this builder
         */
        public Builder addClasspath(Path... entries) {
            for (Path entry : entries) {
                if (entry != null) {
                    classpath.add(entry.toString());
                }
            }
            return this;
        }

        /**
         * Writes class files to the given directory instead of keeping them in memory.
         *
         * @param directory output directory
         * @return this builder
         */
        public Builder outputDirectory(Path directory) {
            this.outputDirectory = directory;
            return this;
        }

        /**
         * Adds options for the Java compiler.
         *
         * @param options compiler options
         * @return this builder
         */
        public Builder javacOptions(String... options) {
            for (String option : options) {
                if (option != null && !option.isBlank()) {
                    javacOptions.add(option);
                }
            }
            return this;
        }

        /**
         * @return the request
         */
        public AliasCompilationRequest build() {
            return new AliasCompilationRequest(this);
        }
    }
}
