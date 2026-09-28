package io.jalias.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineIndexTest {

    @Test
    void splitsUnixLineEndings() {
        LineIndex lines = new LineIndex("class A {\n}\n");
        assertEquals(3, lines.lineCount());
        assertEquals("class A {", lines.lineContent(0));
        assertEquals("\n", lines.lineTerminator(0));
        assertEquals("}", lines.lineContent(1));
        assertEquals("", lines.lineContent(2));
        assertEquals("class A {\n}\n", lines.reassemble(lines.copyContents()));
    }

    @Test
    void remembersWindowsLineEndings() {
        LineIndex lines = new LineIndex("a\r\nb");
        assertEquals(2, lines.lineCount());
        assertEquals("\r\n", lines.lineTerminator(0));
        assertEquals("b", lines.lineContent(1));
        assertEquals("a\r\nb", lines.reassemble(lines.copyContents()));
    }

    @Test
    void handlesTextWithoutTerminator() {
        LineIndex lines = new LineIndex("single");
        assertEquals(1, lines.lineCount());
        assertEquals(0, lines.lineStart(0));
        assertEquals("", lines.lineTerminator(0));
    }

    @Test
    void computesLineAndColumnForOffsets() {
        String text = "abc\ndefg\nhi";
        LineIndex lines = new LineIndex(text);
        assertEquals(0, lines.lineOf(0));
        assertEquals(0, lines.lineOf(3));
        assertEquals(1, lines.lineOf(4));
        assertEquals(2, lines.lineOf(9));
        assertEquals(1, lines.lineNumber(0));
        assertEquals(2, lines.lineNumber(5));
        assertEquals(1, lines.columnNumber(4));
        assertEquals(3, lines.columnNumber(6));
    }

    @Test
    void reassemblesEditedLines() {
        String original = "one\ntwo\nthree";
        LineIndex lines = new LineIndex(original);
        List<String> contents = lines.copyContents();
        contents.set(1, "TWO");
        assertEquals("one\nTWO\nthree", lines.reassemble(contents));
    }

    @Test
    void rejectsWrongLineCount() {
        LineIndex lines = new LineIndex("one\ntwo");
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> lines.reassemble(List.of("one")));
        assertTrue(failure.getMessage().contains("Expected 2 lines"));
    }
}
