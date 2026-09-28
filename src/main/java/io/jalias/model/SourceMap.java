package io.jalias.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Maps offsets between an <em>original</em> source text and the <em>normalised</em> variant that
 * JAlias hands to the Java compiler.
 *
 * <p>Normalisation exists because {@code import com.foo.Bar as FooBar;} is not valid Java. JAlias
 * turns every such statement into a comment that occupies exactly the same lines, which means that
 * the compiler sees valid Java while all other positions (including the ones it reports in
 * diagnostics) still line up with the file the developer wrote.
 *
 * @see io.jalias.parser.AliasImportParser
 */
public final class SourceMap {

    private record Edit(int originalOffset, int originalLength, int normalizedOffset, int normalizedLength) {
    }

    private final String originalText;
    private final String normalizedText;
    private final List<Edit> edits;

    private SourceMap(String originalText, String normalizedText, List<Edit> edits) {
        this.originalText = originalText;
        this.normalizedText = normalizedText;
        this.edits = List.copyOf(edits);
    }

    /**
     * Creates a map for a text that was not modified.
     *
     * @param text the text
     * @return an identity map
     */
    public static SourceMap identity(String text) {
        return new SourceMap(Objects.requireNonNull(text, "text"), text, List.of());
    }

    /**
     * Creates a map from a list of edits. Edits must not overlap; they are sorted by the caller's
     * region offsets.
     *
     * @param originalText   original text
     * @param normalizedText normalised text
     * @param originalRanges start and exclusive end offsets of the edited regions in the original text
     * @param normalizedRanges start and exclusive end offsets of the corresponding regions in the
     *                         normalised text; the number of entries must match {@code originalRanges}
     * @return the map
     */
    public static SourceMap of(String originalText, String normalizedText, List<int[]> originalRanges,
                               List<int[]> normalizedRanges) {
        if (originalRanges.size() != normalizedRanges.size()) {
            throw new IllegalArgumentException("Region counts differ: " + originalRanges.size() + " vs "
                    + normalizedRanges.size());
        }
        List<Edit> edits = new ArrayList<>();
        for (int i = 0; i < originalRanges.size(); i++) {
            int[] original = originalRanges.get(i);
            int[] normalized = normalizedRanges.get(i);
            edits.add(new Edit(original[0], original[1] - original[0], normalized[0], normalized[1] - normalized[0]));
        }
        edits.sort(Comparator.comparingInt(Edit::normalizedOffset));
        return new SourceMap(originalText, normalizedText, edits);
    }

    /**
     * @return the text the developer wrote
     */
    public String originalText() {
        return originalText;
    }

    /**
     * @return the text handed to the Java compiler, invalid alias syntax replaced by comments
     */
    public String normalizedText() {
        return normalizedText;
    }

    /**
     * @return the number of edited regions
     */
    public int editCount() {
        return edits.size();
    }

    /**
     * Translates an offset of the normalised text into the matching offset of the original text.
     *
     * @param normalizedOffset offset into {@link #normalizedText()}
     * @return the matching offset in {@link #originalText()}
     */
    public int toOriginalOffset(int normalizedOffset) {
        int delta = 0;
        for (Edit edit : edits) {
            int normalizedEnd = edit.normalizedOffset() + edit.normalizedLength();
            if (normalizedOffset >= normalizedEnd) {
                delta += edit.originalLength() - edit.normalizedLength();
            } else if (normalizedOffset >= edit.normalizedOffset()) {
                int column = Math.min(normalizedOffset - edit.normalizedOffset(), edit.originalLength());
                return edit.originalOffset() + column;
            } else {
                break;
            }
        }
        return normalizedOffset + delta;
    }

    /**
     * Translates an offset of the original text into the matching offset of the normalised text.
     *
     * @param originalOffset offset into {@link #originalText()}
     * @return the matching offset in {@link #normalizedText()}
     */
    public int toNormalizedOffset(int originalOffset) {
        int delta = 0;
        for (Edit edit : edits) {
            int originalEnd = edit.originalOffset() + edit.originalLength();
            if (originalOffset >= originalEnd) {
                delta += edit.normalizedLength() - edit.originalLength();
            } else if (originalOffset >= edit.originalOffset()) {
                int column = Math.min(originalOffset - edit.originalOffset(), edit.normalizedLength());
                return edit.normalizedOffset() + column;
            } else {
                break;
            }
        }
        return originalOffset + delta;
    }

    @Override
    public String toString() {
        return "SourceMap[edits=" + edits.size() + "]";
    }
}
