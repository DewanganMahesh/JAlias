package io.jalias.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeNamesTest {

    @Test
    void acceptsOrdinaryIdentifiers() {
        assertTrue(TypeNames.isValidIdentifier("Bar"));
        assertTrue(TypeNames.isValidIdentifier("_bar$1"));
        assertTrue(TypeNames.isValidAlias("FooBar"));
        assertFalse(TypeNames.isValidIdentifier(""));
        assertFalse(TypeNames.isValidIdentifier("1Bar"));
        assertFalse(TypeNames.isValidIdentifier("Foo-Bar"));
        assertFalse(TypeNames.isValidIdentifier(null));
    }

    @Test
    void rejectsKeywordsAndLiterals() {
        assertTrue(TypeNames.isKeyword("class"));
        assertTrue(TypeNames.isKeyword("null"));
        assertTrue(TypeNames.isKeyword("_"));
        assertFalse(TypeNames.isKeyword("FooBar"));
        assertFalse(TypeNames.isValidAlias("class"));
        assertFalse(TypeNames.isValidAlias("true"));
    }

    @Test
    void validatesQualifiedNamesSegmentBySegment() {
        assertTrue(TypeNames.isValidQualifiedName("com.foo.Bar"));
        assertTrue(TypeNames.isValidQualifiedName("Bar"));
        assertTrue(TypeNames.isValidQualifiedName("java.util.AbstractMap.SimpleEntry"));
        assertFalse(TypeNames.isValidQualifiedName("com..Bar"));
        assertFalse(TypeNames.isValidQualifiedName(".Bar"));
        assertFalse(TypeNames.isValidQualifiedName("com.foo.Bar."));
        assertFalse(TypeNames.isValidQualifiedName("com.foo.Bar; import x"));
        assertFalse(TypeNames.isValidQualifiedName(""));
    }

    @Test
    void splitsSimpleNames() {
        assertEquals("Bar", TypeNames.simpleNameOf("com.foo.Bar"));
        assertEquals("Bar", TypeNames.simpleNameOf("Bar"));
        assertEquals("Inner", TypeNames.simpleNameOf("com.foo.Outer$Inner"));
        assertEquals("nested", TypeNames.simpleNameOf("com.foo.nested"));
    }

    @Test
    void detectsQualifiedAndAliasLikeNames() {
        assertTrue(TypeNames.isQualified("com.foo.Bar"));
        assertFalse(TypeNames.isQualified("Bar"));
        assertFalse(TypeNames.isQualified(null));
        assertTrue(TypeNames.looksLikeAlias("FooBar"));
        assertFalse(TypeNames.looksLikeAlias("fooBar"));
        assertFalse(TypeNames.looksLikeAlias("class"));
        assertFalse(TypeNames.looksLikeAlias(null));
    }
}
