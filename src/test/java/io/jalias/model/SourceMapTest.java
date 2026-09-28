package io.jalias.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceMapTest {

    @Test
    void identityMapKeepsOffsets() {
        SourceMap map = SourceMap.identity("class A {}\n");
        assertEquals(0, map.editCount());
        assertEquals(5, map.toOriginalOffset(5));
        assertEquals(5, map.toNormalizedOffset(5));
        assertEquals("class A {}\n", map.originalText());
        assertEquals("class A {}\n", map.normalizedText());
    }

    @Test
    void mapsOffsetsAroundAnEditedLine() {
        String original = "import a.B as C;\nclass X { C c; }\n";
        String normalized = "//port a.B as C;\nclass X { C c; }\n";
        SourceMap map = SourceMap.of(original, normalized,
                List.of(new int[] {0, 18}), List.of(new int[] {0, 18}));

        assertEquals(1, map.editCount());
        assertEquals(20, map.toOriginalOffset(20));
        assertEquals(18, map.toOriginalOffset(18));
        assertEquals(0, map.toOriginalOffset(0));
        assertEquals(20, map.toNormalizedOffset(20));
    }

    @Test
    void mapsOffsetsWhenTheReplacementIsShorter() {
        String original = "import a.B as C;\nclass X { C c; }\n";
        String normalized = "//\nclass X { C c; }\n";
        SourceMap map = SourceMap.of(original, normalized,
                List.of(new int[] {0, 18}), List.of(new int[] {0, 2}));

        assertEquals(0, map.toOriginalOffset(0));
        assertEquals(19, map.toOriginalOffset(3));
        assertEquals(21, map.toOriginalOffset(5));
        assertEquals(3, map.toNormalizedOffset(19));
        assertEquals(0, map.toNormalizedOffset(0));
    }

    @Test
    void mapsOffsetsSafelyInsideAReplacedRegion() {
        SourceMap map = SourceMap.of("abcdef", "ab",
                List.of(new int[] {2, 6}), List.of(new int[] {2, 2}));
        assertEquals(6, map.toOriginalOffset(2));
        assertEquals(2, map.toNormalizedOffset(5));
    }
}
