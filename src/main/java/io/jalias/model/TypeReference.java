package io.jalias.model;

import io.jalias.exceptions.AliasNotFoundException;
import io.jalias.resolver.AliasResolver;

import java.util.Objects;

/**
 * A reference to a type as it appears in source code.
 *
 * <p>A reference is either an alias ({@code FooBar}) or an ordinary qualified name
 * ({@code com.foo.Bar}). Resolving a reference through an {@link AliasResolver} turns it into the
 * fully qualified name of a real class.
 *
 * @param name      the name as written, for example {@code FooBar} or {@code com.foo.Bar}
 * @param line      one based line number of the reference, {@code 0} when unknown
 * @param column    one based column number of the reference, {@code 0} when unknown
 */
public record TypeReference(String name, int line, int column) {

    /**
     * Validates the reference.
     */
    public TypeReference {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A type reference must not be empty");
        }
    }

    /**
     * Creates a reference without position information.
     *
     * @param name the name as written
     * @return the reference
     */
    public static TypeReference of(String name) {
        return new TypeReference(name, 0, 0);
    }

    /**
     * @return {@code true} if the reference is written as a qualified name rather than an alias
     */
    public boolean isQualified() {
        return TypeNames.isQualified(name);
    }

    /**
     * @return the last segment of the reference
     */
    public String simpleName() {
        return TypeNames.simpleNameOf(name);
    }

    /**
     * Checks whether the reference points at an alias known to the resolver.
     *
     * @param resolver resolver to consult
     * @return {@code true} if an alias with this name is declared
     */
    public boolean isAlias(AliasResolver resolver) {
        return resolver.isAlias(name);
    }

    /**
     * Resolves this reference to the fully qualified name of a real class.
     *
     * @param resolver resolver holding the alias declarations
     * @return the fully qualified name the reference stands for
     * @throws AliasNotFoundException if the reference is a simple name that is not a declared alias
     */
    public String resolve(AliasResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        ImportAlias alias = resolver.find(name).orElse(null);
        if (alias != null) {
            return alias.qualifiedName();
        }
        if (isQualified()) {
            return name;
        }
        throw new AliasNotFoundException("Unknown alias '" + name + "'. Known aliases: " + resolver.aliases()
                + (line > 0 ? " (referenced at line " + line + ")" : ""));
    }

    /**
     * Resolves the reference if it is an alias.
     *
     * @param resolver resolver holding the alias declarations
     * @return the qualified name, or empty when the reference is not an alias
     */
    public java.util.Optional<String> resolveIfAlias(AliasResolver resolver) {
        return resolver.find(name).map(ImportAlias::qualifiedName);
    }

    /**
     * @param fullyQualifiedName the class the reference was resolved to
     * @return a human readable description used in messages and generated sources
     */
    public String describe(String fullyQualifiedName) {
        return name + " -> " + fullyQualifiedName;
    }

    @Override
    public String toString() {
        return name;
    }
}
