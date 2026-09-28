package io.jalias.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, comment and literal aware scanner used by {@link AliasImportParser}.
 *
 * <p>It only understands the tiny part of Java that matters for aliased imports: keywords, tokens
 * made of identifiers, dots and stars, statement terminators, comments and literals. Everything
 * else is left to the real Java parser that runs afterwards, which is the reason this class can stay
 * small and predictable.
 */
final class AliasSourceScanner {

    /**
     * A token found in an import statement.
     *
     * @param text   the token text
     * @param offset offset of the token inside the scanned text
     */
    record Token(String text, int offset) {
    }

    private final String text;

    AliasSourceScanner(String text) {
        this.text = text;
    }

    String text() {
        return text;
    }

    int length() {
        return text.length();
    }

    private char charAt(int pos) {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    /**
     * Skips whitespace and comments.
     *
     * @param pos starting offset
     * @return the offset of the next significant character (or the end of the text)
     */
    int skipTrivia(int pos) {
        int i = pos;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && charAt(i + 1) == '/') {
                i = endOfLineComment(i);
            } else if (c == '/' && charAt(i + 1) == '*') {
                i = endOfBlockComment(i);
            } else {
                break;
            }
        }
        return i;
    }

    /**
     * @param pos offset to inspect
     * @return {@code true} if a Java identifier starts at {@code pos}
     */
    boolean isWordStart(int pos) {
        return pos < text.length() && Character.isJavaIdentifierStart(text.charAt(pos));
    }

    /**
     * @param pos offset of an identifier
     * @return the offset just after the identifier
     */
    int wordEnd(int pos) {
        int i = pos;
        while (i < text.length() && Character.isJavaIdentifierPart(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Checks whether exactly the given word starts at {@code pos}.
     *
     * @param pos  offset to inspect
     * @param word expected word
     * @return {@code true} on a match at a word boundary
     */
    boolean wordAt(int pos, String word) {
        int end = wordEnd(pos);
        return end - pos == word.length() && text.regionMatches(pos, word, 0, word.length());
    }

    /**
     * Returns the offset after the {@code ;} that terminates the statement starting at {@code pos}.
     *
     * @param pos offset of the statement start
     * @return the offset after the semicolon, or {@code -1} when the statement is not terminated
     *         before the end of the text or a type body
     */
    int findStatementEnd(int pos) {
        int i = pos;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(i);
            } else if (c == '/' && charAt(i + 1) == '/') {
                i = endOfLineComment(i);
            } else if (c == '/' && charAt(i + 1) == '*') {
                i = endOfBlockComment(i);
            } else if (c == ';') {
                return i + 1;
            } else if (c == '{' || c == '}') {
                return -1;
            } else {
                i++;
            }
        }
        return -1;
    }

    /**
     * Skips an annotation, including its argument list when present.
     *
     * @param pos offset of the {@code @}
     * @return the offset just after the annotation
     */
    int skipAnnotation(int pos) {
        int i = pos + 1;
        while (i < text.length() && (Character.isJavaIdentifierPart(text.charAt(i)) || text.charAt(i) == '.')) {
            i++;
        }
        int afterName = skipTrivia(i);
        if (charAt(afterName) != '(') {
            return i;
        }
        int depth = 0;
        int j = afterName;
        while (j < text.length()) {
            char c = text.charAt(j);
            if (c == '"' || c == '\'') {
                j = skipLiteral(j);
                continue;
            }
            if (c == '/' && charAt(j + 1) == '/') {
                j = endOfLineComment(j);
                continue;
            }
            if (c == '/' && charAt(j + 1) == '*') {
                j = endOfBlockComment(j);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return j + 1;
                }
            }
            j++;
        }
        return text.length();
    }

    /**
     * Finds the next occurrence of a keyword outside comments and literals.
     *
     * @param keyword keyword to look for
     * @param from    offset to start searching at
     * @return the offset of the keyword, or {@code -1}
     */
    int indexOfKeyword(String keyword, int from) {
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(i);
            } else if (c == '/' && charAt(i + 1) == '/') {
                i = endOfLineComment(i);
            } else if (c == '/' && charAt(i + 1) == '*') {
                i = endOfBlockComment(i);
            } else if (Character.isJavaIdentifierStart(c)) {
                int end = wordEnd(i);
                if (text.regionMatches(i, keyword, 0, keyword.length()) && end - i == keyword.length()) {
                    return i;
                }
                i = end;
            } else {
                i++;
            }
        }
        return -1;
    }

    /**
     * Reads the tokens of a statement, ignoring comments and the terminating semicolon.
     *
     * @return the tokens of this scanner's text
     */
    List<Token> readTokens() {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while ((i = skipTrivia(i)) < text.length()) {
            char c = text.charAt(i);
            if (c == ';') {
                break;
            }
            Token token = readToken(i);
            tokens.add(token);
            i = token.offset() + token.text().length();
        }
        return tokens;
    }

    private Token readToken(int pos) {
        char c = text.charAt(pos);
        if (Character.isJavaIdentifierStart(c) || c == '.' || c == '*') {
            int i = pos;
            while (i < text.length()) {
                char ch = text.charAt(i);
                if (Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '*') {
                    i++;
                } else {
                    break;
                }
            }
            return new Token(text.substring(pos, i), pos);
        }
        return new Token(String.valueOf(c), pos);
    }

    private int endOfLineComment(int pos) {
        int i = pos + 2;
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    private int endOfBlockComment(int pos) {
        int end = text.indexOf("*/", pos + 2);
        return end < 0 ? text.length() : end + 2;
    }

    /**
     * Skips a string, text block or character literal.
     *
     * @param pos offset of the opening quote
     * @return the offset just after the literal
     */
    private int skipLiteral(int pos) {
        char quote = text.charAt(pos);
        if (quote == '"' && text.startsWith("\"\"\"", pos)) {
            int end = text.indexOf("\"\"\"", pos + 3);
            return end < 0 ? text.length() : end + 3;
        }
        int i = pos + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            if (c == '\n') {
                return i;
            }
            i++;
        }
        return text.length();
    }
}
