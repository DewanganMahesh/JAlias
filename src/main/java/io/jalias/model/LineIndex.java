package io.jalias.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Line oriented view of a source text that remembers how the text was split so that it can be
 * reassembled byte for byte.
 *
 * <p>JAlias relies on the fact that its normalisation step never adds or removes lines: the
 * {@code n}-th line of the normalised source is always the {@code n}-th line of the original source.
 * That invariant is what makes it possible to keep compiler positions meaningful and to rewrite
 * alias references with exact offsets.
 *
 * <p>Line numbers exposed by this class are <em>zero based</em>; the helpers that produce user
 * facing messages convert them to one based values.
 */
public final class LineIndex {

    private final String text;
    private final List<Integer> starts = new ArrayList<>();
    private final List<String> contents = new ArrayList<>();
    private final List<String> terminators = new ArrayList<>();

    /**
     * Splits the given text into lines.
     *
     * @param text source text, must not be {@code null}
     */
    public LineIndex(String text) {
        this.text = Objects.requireNonNull(text, "text");
        int currentStart = 0;
        int i = 0;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                int terminatorEnd = c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                starts.add(currentStart);
                contents.add(text.substring(currentStart, i));
                terminators.add(text.substring(i, terminatorEnd));
                currentStart = terminatorEnd;
                i = terminatorEnd;
            } else {
                i++;
            }
        }
        starts.add(currentStart);
        contents.add(text.substring(currentStart));
        terminators.add("");
    }

    /**
     * @return the split source text
     */
    public String text() {
        return text;
    }

    /**
     * @return the number of lines, counting a trailing empty line when the text ends with a line
     *         terminator
     */
    public int lineCount() {
        return contents.size();
    }

    /**
     * @param line zero based line index
     * @return the offset at which the line contents start
     */
    public int lineStart(int line) {
        return starts.get(line);
    }

    /**
     * @param line zero based line index
     * @return the line contents without the line terminator
     */
    public String lineContent(int line) {
        return contents.get(line);
    }

    /**
     * @param line zero based line index
     * @return the line terminator, empty for the last line when the text does not end with a newline
     */
    public String lineTerminator(int line) {
        return terminators.get(line);
    }

    /**
     * Returns the zero based index of the line containing the given offset.
     *
     * @param offset offset into the text
     * @return zero based line index
     */
    public int lineOf(int offset) {
        int low = 0;
        int high = starts.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (starts.get(mid) <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Returns the one based line number of the given offset, for messages and diagnostics.
     *
     * @param offset offset into the text
     * @return one based line number
     */
    public int lineNumber(int offset) {
        return lineOf(offset) + 1;
    }

    /**
     * Returns the one based column number of the given offset, for messages and diagnostics.
     *
     * @param offset offset into the text
     * @return one based column number
     */
    public int columnNumber(int offset) {
        int line = lineOf(offset);
        return offset - lineStart(line) + 1;
    }

    /**
     * Reassembles a text from line contents, reusing the original line terminators. The given list
     * must have the same size as {@link #lineCount()}.
     *
     * @param newContents replacement line contents
     * @return the joined text
     */
    public String reassemble(List<String> newContents) {
        Objects.requireNonNull(newContents, "newContents");
        if (newContents.size() != contents.size()) {
            throw new IllegalArgumentException("Expected " + contents.size() + " lines but got "
                    + newContents.size());
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < newContents.size(); i++) {
            sb.append(newContents.get(i)).append(terminators.get(i));
        }
        return sb.toString();
    }

    /**
     * @return a mutable copy of the line contents, convenient for line based editing
     */
    public List<String> copyContents() {
        return new ArrayList<>(contents);
    }
}
