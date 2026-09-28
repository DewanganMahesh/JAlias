package io.jalias.compiler;

import io.jalias.model.SourceUnit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The outcome of rewriting one compilation unit.
 *
 * @param source           the unit that was transformed
 * @param originalSource   the source exactly as the developer wrote it
 * @param transformedSource the compilable Java source, with aliases replaced by qualified names
 * @param aliasMappings    alias name to qualified class name, in declaration order
 * @param replacements     how many alias references were rewritten
 */
public record TransformResult(SourceUnit source,
                              String originalSource,
                              String transformedSource,
                              Map<String, String> aliasMappings,
                              int replacements) {

    /**
     * Validates the result.
     */
    public TransformResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(originalSource, "originalSource");
        Objects.requireNonNull(transformedSource, "transformedSource");
        aliasMappings = new LinkedHashMap<>(aliasMappings);
    }

    /**
     * @return {@code true} if the transformation changed anything
     */
    public boolean hasChanges() {
        return !originalSource.equals(transformedSource);
    }

    /**
     * @return a short human readable summary, useful in logs and tests
     */
    public String summary() {
        return source.displayName() + ": " + aliasMappings.size() + " alias(es), " + replacements
                + " reference(s) rewritten";
    }

    @Override
    public String toString() {
        return summary();
    }
}
