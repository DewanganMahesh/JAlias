package io.jalias.model;

import java.util.Optional;

/**
 * An import statement as it was found in a compilation unit, including aliased imports.
 *
 * <p>Plain imports (single, wildcard, static) are informational: JAlias leaves them untouched and
 * they keep working exactly as in normal Java. Aliased imports ({@link ImportKind#ALIAS}) are
 * transformed away and are represented by {@link #aliasTarget()}.
 *
 * @param kind          kind of the import
 * @param qualifiedName for {@link ImportKind#SINGLE} and {@link ImportKind#ALIAS} the fully
 *                      qualified class name, for wildcard imports the qualifier, for static
 *                      imports the owner of the member
 * @param name          the name this import introduces: the simple type name, the static member
 *                      name, the alias, or {@code *}
 * @param rawText       the import statement exactly as written, including the trailing semicolon
 * @param line          one based line number
 * @param column        one based column number
 */
public record ImportStatement(ImportKind kind, String qualifiedName, String name, String rawText,
                              int line, int column) {

    /**
     * Creates a single type import ({@code import com.foo.Bar;}).
     *
     * @param qualifiedName fully qualified class name
     * @param rawText       original text
     * @param line          one based line
     * @param column        one based column
     * @return the parsed statement
     */
    public static ImportStatement single(String qualifiedName, String rawText, int line, int column) {
        return new ImportStatement(ImportKind.SINGLE, qualifiedName, TypeNames.simpleNameOf(qualifiedName),
                rawText, line, column);
    }

    /**
     * Creates a wildcard import ({@code import com.foo.*;}).
     *
     * @param qualifier package or type prefix
     * @param rawText   original text
     * @param line      one based line
     * @param column    one based column
     * @return the parsed statement
     */
    public static ImportStatement wildcard(String qualifier, String rawText, int line, int column) {
        return new ImportStatement(ImportKind.WILDCARD, qualifier, "*", rawText, line, column);
    }

    /**
     * Creates a static import.
     *
     * @param owner   type that owns the member
     * @param name    member name or {@code *}
     * @param wildcard whether the import is a wildcard static import
     * @param rawText original text
     * @param line    one based line
     * @param column  one based column
     * @return the parsed statement
     */
    public static ImportStatement staticImport(String owner, String name, boolean wildcard, String rawText,
                                               int line, int column) {
        return new ImportStatement(wildcard ? ImportKind.STATIC_WILDCARD : ImportKind.STATIC, owner, name,
                rawText, line, column);
    }

    /**
     * Creates an aliased import.
     *
     * @param alias   the alias declaration
     * @param rawText original text
     * @param line    one based line
     * @param column  one based column
     * @return the parsed statement
     */
    public static ImportStatement alias(ImportAlias alias, String rawText, int line, int column) {
        return new ImportStatement(ImportKind.ALIAS, alias.qualifiedName(), alias.alias(), rawText, line, column);
    }

    /**
     * @return {@code true} if this is a wildcard import (static or not)
     */
    public boolean isWildcard() {
        return kind == ImportKind.WILDCARD || kind == ImportKind.STATIC_WILDCARD;
    }

    /**
     * @return {@code true} if this is a static import
     */
    public boolean isStatic() {
        return kind == ImportKind.STATIC || kind == ImportKind.STATIC_WILDCARD;
    }

    /**
     * @return {@code true} if this is an aliased import
     */
    public boolean isAlias() {
        return kind == ImportKind.ALIAS;
    }

    /**
     * Returns the name introduced by a plain type import, used to detect collisions with aliases.
     *
     * @return the simple name of a single type import, empty for everything else
     */
    public Optional<String> importedSimpleName() {
        return kind == ImportKind.SINGLE ? Optional.of(name) : Optional.empty();
    }

    /**
     * Returns the alias when this is an aliased import.
     *
     * @return the alias declaration, or empty
     */
    public Optional<ImportAlias> aliasTarget() {
        if (kind != ImportKind.ALIAS) {
            return Optional.empty();
        }
        return Optional.of(new ImportAlias(qualifiedName, name, "<parsed>", line, column));
    }

    @Override
    public String toString() {
        return rawText;
    }
}
