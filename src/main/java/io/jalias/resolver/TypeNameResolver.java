package io.jalias.resolver;

import io.jalias.model.TypeNames;

/**
 * Name level questions the resolver layer has to answer: is this a usable alias, is this a usable
 * import target, does this name look like a type rather than a variable.
 *
 * <p>The methods delegate to {@link TypeNames} and exist so that callers in the resolver and
 * compiler packages can express intent without knowing about the model helpers.
 */
public final class TypeNameResolver {

    private TypeNameResolver() {
    }

    /**
     * @param name candidate alias
     * @return {@code true} if the name is a valid, non-reserved Java identifier
     */
    public static boolean isValidAlias(String name) {
        return TypeNames.isValidAlias(name);
    }

    /**
     * @param name candidate import target
     * @return {@code true} if the name is a syntactically valid qualified class name
     */
    public static boolean isValidImportTarget(String name) {
        return TypeNames.isValidQualifiedName(name);
    }

    /**
     * @param qualifiedName qualified or binary class name
     * @return the simple name of the class
     */
    public static String simpleNameOf(String qualifiedName) {
        return TypeNames.simpleNameOf(qualifiedName);
    }

    /**
     * @param name name found in source code
     * @return {@code true} if the name follows the type naming convention and could be an alias
     */
    public static boolean looksLikeAlias(String name) {
        return TypeNames.looksLikeAlias(name);
    }

    /**
     * @param qualifiedName name to inspect
     * @return {@code true} if the name contains a dot
     */
    public static boolean isQualified(String qualifiedName) {
        return TypeNames.isQualified(qualifiedName);
    }
}
