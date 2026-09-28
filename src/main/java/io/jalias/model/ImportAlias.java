package io.jalias.model;

import io.jalias.exceptions.AliasSyntaxException;

/**
 * A single aliased import such as {@code import com.foo.Bar as FooBar;}.
 *
 * <p>The alias is a pure compile-time concept: it exists in the source that a developer writes and
 * is replaced by {@link #qualifiedName()} before the Java compiler sees the code. Nothing about the
 * aliased type is changed at runtime.
 *
 * @param qualifiedName the fully qualified name of the imported class, never a simple name that is
 *                      itself an alias (see {@link io.jalias.exceptions.CircularAliasException})
 * @param alias         the name used in the source code
 * @param sourceName    file (or synthetic unit) the alias was declared in, used for error messages
 * @param line          one based line number of the aliased import, {@code 0} when unknown
 * @param column        one based column number of the aliased import, {@code 0} when unknown
 */
public record ImportAlias(String qualifiedName, String alias, String sourceName, int line, int column) {

    /** Placeholder used when the source is not backed by a file. */
    public static final String INLINE_SOURCE = "<inline>";

    /**
     * Validates the alias declaration.
     *
     * @throws AliasSyntaxException if the qualified name or the alias is not usable
     */
    public ImportAlias {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            throw new AliasSyntaxException("The target of an aliased import must not be empty: " + describe(alias, sourceName, line));
        }
        if (!TypeNames.isValidQualifiedName(qualifiedName)) {
            throw new AliasSyntaxException(
                    "Invalid import target '" + qualifiedName + "' in " + describe(alias, sourceName, line)
                            + ": expected a qualified class name such as com.foo.Bar");
        }
        if (alias == null || alias.isBlank()) {
            throw new AliasSyntaxException(
                    "The alias of '" + qualifiedName + "' must not be empty in " + describe(alias, sourceName, line));
        }
        if (TypeNames.isKeyword(alias)) {
            throw new AliasSyntaxException(
                    "Invalid alias '" + alias + "' in " + describe(alias, sourceName, line)
                            + ": Java keywords cannot be used as aliases");
        }
        if (!TypeNames.isValidIdentifier(alias)) {
            throw new AliasSyntaxException(
                    "Invalid alias '" + alias + "' in " + describe(alias, sourceName, line)
                            + ": not a valid Java identifier");
        }
    }

    /**
     * Creates an alias without source information, convenient for tests and for programmatic use.
     *
     * @param qualifiedName fully qualified class name
     * @param alias         alias to use in source code
     */
    public ImportAlias(String qualifiedName, String alias) {
        this(qualifiedName, alias, INLINE_SOURCE, 0, 0);
    }

    /**
     * Factory for {@link #ImportAlias(String, String)}.
     *
     * @param qualifiedName fully qualified class name
     * @param alias         alias to use in source code
     * @return the alias declaration
     */
    public static ImportAlias of(String qualifiedName, String alias) {
        return new ImportAlias(qualifiedName, alias);
    }

    /**
     * Returns the simple name of the aliased class, for example {@code Bar}.
     *
     * @return the simple name of {@link #qualifiedName()}
     */
    public String simpleName() {
        return TypeNames.simpleNameOf(qualifiedName);
    }

    /**
     * Returns the alias declaration as it appears in source code.
     *
     * @return {@code import <qualifiedName> as <alias>;}
     */
    public String declaration() {
        return "import " + qualifiedName + " as " + alias + ";";
    }

    /**
     * Returns the location of the declaration for error messages.
     *
     * @return {@code file:line} or just the file name when no line is known
     */
    public String location() {
        return line > 0 ? sourceName + ":" + line : sourceName;
    }

    /**
     * Returns the declaration, the canonical textual form of an alias.
     *
     * @return {@link #declaration()}
     */
    @Override
    public String toString() {
        return declaration();
    }

    private static String describe(String alias, String sourceName, int line) {
        String where = line > 0 ? sourceName + ":" + line : String.valueOf(sourceName);
        return "an aliased import in " + where + (alias == null ? "" : " for alias '" + alias + "'");
    }
}
