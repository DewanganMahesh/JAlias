package io.jalias.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One compilation unit that may use alias syntax.
 *
 * <p>The binary name matters: the Java compiler insists that a public type is declared in a file
 * named after it, so the in-memory representation created from a {@code SourceUnit} uses
 * {@link #binaryName()} to derive its file name. Use {@link #fromPath(Path)} when the source comes
 * from a real file, or pass the binary name explicitly for generated sources.
 *
 * @param binaryName binary name of the primary type, for example {@code com.foo.Example}
 * @param sourceText the Java source, alias syntax included
 * @param origin     the file the source was read from, or {@code null} for in-memory sources
 */
public record SourceUnit(String binaryName, String sourceText, Path origin) {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.$]+)\\s*;");
    private static final Pattern TYPE = Pattern.compile(
            "(?m)^\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*(?:public\\s+|protected\\s+|private\\s+|final\\s+|abstract\\s+"
                    + "|sealed\\s+|non-sealed\\s+|strictfp\\s+)*(?:class|interface|enum|record|@interface)\\s+([\\w$]+)");

    /**
     * Validates the unit.
     */
    public SourceUnit {
        Objects.requireNonNull(binaryName, "binaryName");
        Objects.requireNonNull(sourceText, "sourceText");
        if (binaryName.isBlank()) {
            throw new IllegalArgumentException("The binary name of a source unit must not be empty");
        }
        if (!TypeNames.isValidQualifiedName(binaryName)) {
            throw new IllegalArgumentException("Invalid binary name '" + binaryName + "'");
        }
    }

    /**
     * Creates an in-memory unit.
     *
     * @param binaryName binary name of the primary type
     * @param sourceText Java source
     * @return the unit
     */
    public static SourceUnit of(String binaryName, String sourceText) {
        return new SourceUnit(binaryName, sourceText, null);
    }

    /**
     * Creates an in-memory unit, guessing the binary name from the package declaration and the first
     * type declaration of the source.
     *
     * @param sourceText Java source
     * @return the unit
     */
    public static SourceUnit of(String sourceText) {
        return new SourceUnit(inferBinaryName(sourceText), sourceText, null);
    }

    /**
     * Reads a compilation unit from disk using UTF-8.
     *
     * @param path path of a {@code .java} file
     * @return the unit, with its binary name derived from the package declaration and file name
     * @throws IOException if the file cannot be read
     */
    public static SourceUnit fromPath(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        String text = Files.readString(path, StandardCharsets.UTF_8);
        String fileName = path.getFileName().toString();
        String simpleName = fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - ".java".length())
                : fileName;
        Matcher packageMatcher = PACKAGE.matcher(text);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
        String binaryName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        return new SourceUnit(binaryName, text, path.toAbsolutePath().normalize());
    }

    /**
     * Guesses the binary name of a source text.
     *
     * @param sourceText Java source
     * @return package plus the first declared type, or {@code UnnamedUnit} when nothing was found
     */
    public static String inferBinaryName(String sourceText) {
        Objects.requireNonNull(sourceText, "sourceText");
        Matcher packageMatcher = PACKAGE.matcher(sourceText);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
        Matcher typeMatcher = TYPE.matcher(sourceText);
        String simpleName = typeMatcher.find() ? typeMatcher.group(1) : "UnnamedUnit";
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    /**
     * @return the package of the primary type, empty when unnamed
     */
    public String packageName() {
        return TypeNames.isQualified(binaryName) ? binaryName.substring(0, binaryName.lastIndexOf('.')) : "";
    }

    /**
     * @return the simple name of the primary type
     */
    public String simpleName() {
        return TypeNames.simpleNameOf(binaryName);
    }

    /**
     * @return the file name the compiler should assume for this unit
     */
    public String fileName() {
        return simpleName() + ".java";
    }

    /**
     * @return a display name for diagnostics
     */
    public String displayName() {
        return origin != null ? origin.toString() : fileName();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
