package io.jalias.model;

import java.util.List;
import java.util.Set;

/**
 * Utility methods for the Java names JAlias has to reason about: aliases, qualified class names
 * and simple names.
 *
 * <p>All methods are pure and side effect free.
 */
public final class TypeNames {

    /** Java keywords plus the {@code true}/{@code false}/{@code null} literals and {@code _}. */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "_");

    private TypeNames() {
    }

    /**
     * Checks whether the given text is one of the Java keywords (or one of the literals
     * {@code true}, {@code false}, {@code null}, or the reserved {@code _}).
     *
     * @param name text to check
     * @return {@code true} if the text is reserved by the Java language
     */
    public static boolean isKeyword(String name) {
        return name != null && KEYWORDS.contains(name);
    }

    /**
     * Checks whether the given text is a syntactically valid Java identifier.
     *
     * @param name text to check
     * @return {@code true} if the text is a valid, non-reserved identifier
     */
    public static boolean isValidIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether the given text can be used as an alias: a valid identifier that is not a
     * Java keyword.
     *
     * @param name text to check
     * @return {@code true} if the text can be used as an alias
     */
    public static boolean isValidAlias(String name) {
        return isValidIdentifier(name) && !isKeyword(name);
    }

    /**
     * Checks whether the given text is a valid qualified name, that is one or more identifiers
     * separated by dots ({@code com.foo.Bar}, {@code Bar}, {@code java.util.Map.Entry}).
     *
     * @param qualifiedName text to check
     * @return {@code true} if every segment is a valid identifier
     */
    public static boolean isValidQualifiedName(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return false;
        }
        for (String segment : split(qualifiedName)) {
            if (!isValidIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether the given name contains at least one dot.
     *
     * @param name text to check
     * @return {@code true} if the name is qualified
     */
    public static boolean isQualified(String name) {
        return name != null && name.indexOf('.') >= 0;
    }

    /**
     * Returns the simple name of a qualified name or binary name, for example {@code Bar} for both
     * {@code com.foo.Bar} and {@code com.foo.Outer$Bar}, and {@code Inner} for
     * {@code com.foo.Outer$Inner}.
     *
     * @param qualifiedName qualified or binary class name
     * @return the last name segment
     */
    public static String simpleNameOf(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int separator = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
        return separator >= 0 ? qualifiedName.substring(separator + 1) : qualifiedName;
    }

    /**
     * Heuristic used to map compiler diagnostics back to aliases: typical alias names follow the
     * type naming convention and start with an upper case letter.
     *
     * @param name text to check
     * @return {@code true} if the text looks like a type-ish alias name
     */
    public static boolean looksLikeAlias(String name) {
        if (name == null || name.isEmpty() || !isValidIdentifier(name)) {
            return false;
        }
        return Character.isUpperCase(name.charAt(0));
    }

    private static List<String> split(String qualifiedName) {
        return List.of(qualifiedName.split("\\.", -1));
    }
}
