package io.jalias.resolver;

import io.jalias.exceptions.AliasConflictException;
import io.jalias.exceptions.AliasNotFoundException;
import io.jalias.exceptions.CircularAliasException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.model.ImportAlias;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasRegistryTest {

    @Test
    void registersAndFindsAliases() {
        AliasRegistry registry = new AliasRegistry()
                .register(ImportAlias.of("com.foo.Bar", "FooBar"))
                .register(ImportAlias.of("com.example.Bar", "ExampleBar"));

        assertEquals(2, registry.size());
        assertFalse(registry.isEmpty());
        assertTrue(registry.isAlias("FooBar"));
        assertFalse(registry.isAlias("Bar"));
        assertEquals("com.foo.Bar", registry.resolveTypeName("FooBar"));
        assertEquals("com.example.Bar", registry.find("ExampleBar").orElseThrow().qualifiedName());
        assertEquals(Map.of("FooBar", "com.foo.Bar", "ExampleBar", "com.example.Bar"), registry.mappings());
        assertEquals(List.of("FooBar", "ExampleBar"), List.copyOf(registry.aliasNames()));
        assertEquals(2, registry.aliasesInOrder().size());
        assertEquals(List.of("ExampleBar", "FooBar"), registry.aliases());

        registry.clear();
        assertTrue(registry.isEmpty());
    }

    @Test
    void reportsTheDocumentedMessageForADuplicateAlias() {
        AliasRegistry registry = new AliasRegistry().register(ImportAlias.of("com.foo.Bar", "FooBar"));
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> registry.register(ImportAlias.of("com.example.Bar", "FooBar")));
        assertEquals("Alias 'FooBar' is already mapped to com.foo.Bar", failure.getMessage());
    }

    @Test
    void reportsADuplicateAliasedImport() {
        ImportAlias alias = ImportAlias.of("com.foo.Bar", "FooBar");
        AliasRegistry registry = new AliasRegistry().register(alias);
        AliasConflictException failure =
                assertThrows(AliasConflictException.class, () -> registry.register(alias));
        assertTrue(failure.getMessage().startsWith("Duplicate aliased import: alias 'FooBar' is already mapped to "
                + "com.foo.Bar"), failure.getMessage());
    }

    @Test
    void reportsACircularTargetThatIsAlreadyAnAlias() {
        AliasRegistry registry = new AliasRegistry().register(ImportAlias.of("com.foo.Bar", "FooBar"));
        CircularAliasException failure = assertThrows(CircularAliasException.class,
                () -> registry.register(ImportAlias.of("FooBar", "Bar")));
        assertTrue(failure.getMessage().contains("Circular alias resolution detected"), failure.getMessage());
        assertTrue(failure.getMessage().contains("'FooBar' is itself an alias for com.foo.Bar"),
                failure.getMessage());
    }

    @Test
    void reportsAnAliasThatIsTheTargetOfAnotherAlias() {
        AliasRegistry registry = new AliasRegistry().register(ImportAlias.of("FooBar", "Bar"));
        CircularAliasException failure = assertThrows(CircularAliasException.class,
                () -> registry.register(ImportAlias.of("com.foo.Bar", "FooBar")));
        assertTrue(failure.getMessage().contains("Circular alias resolution detected"), failure.getMessage());
        assertTrue(failure.getMessage().contains("'FooBar' is the import target of 'Bar'"), failure.getMessage());
    }

    @Test
    void registersManyAliasesAtOnce() {
        AliasRegistry registry = new AliasRegistry().registerAll(List.of(
                ImportAlias.of("java.util.Date", "UtilDate"),
                ImportAlias.of("java.sql.Date", "SqlDate")));
        assertEquals(2, registry.size());
        assertThrows(AliasConflictException.class, () -> registry.registerAll(List.of(
                ImportAlias.of("java.util.Date", "UtilDate"))));
    }

    @Test
    void resolveClassLoadsTheRealClass() {
        AliasRegistry registry = new AliasRegistry().register(ImportAlias.of("java.util.Date", "UtilDate"));
        assertEquals(java.util.Date.class, registry.resolveClass("UtilDate"));
        assertEquals(java.util.Date.class, registry.resolveClass("UtilDate", getClass().getClassLoader()));
    }

    @Test
    void resolveClassReportsUnknownAndUnresolvableAliases() {
        AliasRegistry registry = new AliasRegistry().register(ImportAlias.of("com.does.not.Exist", "Missing"));
        AliasNotFoundException unknown = assertThrows(AliasNotFoundException.class,
                () -> registry.resolveClass("Nope"));
        assertTrue(unknown.getMessage().contains("Unknown alias 'Nope'"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("Missing"), unknown.getMessage());

        InvalidImportException unresolvable = assertThrows(InvalidImportException.class,
                () -> registry.resolveClass("Missing"));
        assertTrue(unresolvable.getMessage().contains("Cannot resolve aliased class 'com.does.not.Exist'"),
                unresolvable.getMessage());
    }

    @Test
    void registerIfAbsentKeepsTheFirstMapping() {
        AliasRegistry registry = new AliasRegistry()
                .registerIfAbsent(ImportAlias.of("com.foo.Bar", "Bar"))
                .registerIfAbsent(ImportAlias.of("com.example.Bar", "Bar"));
        assertEquals(1, registry.size());
        assertEquals("com.foo.Bar", registry.resolveTypeName("Bar"));
    }

    @Test
    void requireFailsForUnknownAliases() {
        AliasRegistry registry = new AliasRegistry().register(ImportAlias.of("com.foo.Bar", "FooBar"));
        AliasNotFoundException failure =
                assertThrows(AliasNotFoundException.class, () -> registry.require("ExampleBar"));
        assertTrue(failure.getMessage().contains("Unknown alias 'ExampleBar'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("FooBar"), failure.getMessage());
    }
}
