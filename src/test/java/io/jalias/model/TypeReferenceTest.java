package io.jalias.model;

import io.jalias.exceptions.AliasNotFoundException;
import io.jalias.resolver.AliasResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeReferenceTest {

    private final AliasResolver resolver = AliasResolver.of(
            List.of(ImportAlias.of("com.foo.Bar", "FooBar"), ImportAlias.of("java.sql.Date", "SqlDate")));

    @Test
    void resolvesAliases() {
        assertEquals("com.foo.Bar", TypeReference.of("FooBar").resolve(resolver));
        assertEquals("java.sql.Date", TypeReference.of("SqlDate").resolve(resolver));
    }

    @Test
    void keepsQualifiedNamesAsTheyAre() {
        TypeReference reference = TypeReference.of("com.example.Bar");
        assertTrue(reference.isQualified());
        assertEquals("com.example.Bar", reference.resolve(resolver));
        assertFalse(reference.isAlias(resolver));
    }

    @Test
    void reportsUnknownAliases() {
        TypeReference reference = new TypeReference("ExampleBar", 12, 9);
        assertFalse(reference.isAlias(resolver));
        AliasNotFoundException failure =
                assertThrows(AliasNotFoundException.class, () -> reference.resolve(resolver));
        assertTrue(failure.getMessage().contains("Unknown alias 'ExampleBar'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("FooBar"), failure.getMessage());
        assertTrue(failure.getMessage().contains("line 12"), failure.getMessage());
        assertTrue(reference.resolveIfAlias(resolver).isEmpty());
    }

    @Test
    void reportsTheResolvedName() {
        TypeReference reference = TypeReference.of("FooBar");
        assertEquals("FooBar -> com.foo.Bar", reference.describe(reference.resolve(resolver)));
        assertEquals("FooBar", reference.simpleName());
        assertEquals("com.foo.Bar", reference.resolveIfAlias(resolver).orElseThrow());
    }

    @Test
    void rejectsAnEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> TypeReference.of(""));
    }
}
