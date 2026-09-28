package io.jalias.imports;

import io.jalias.exceptions.AliasConflictException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.SourceUnit;
import io.jalias.parser.AliasImportParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportResolverTest {

    private final ImportResolver resolver = new ImportResolver();
    private final AliasImportParser parser = new AliasImportParser();

    @Test
    void resolvesSimpleNamesFromSingleImports() {
        CompilationUnitModel unit = parser.parse(SourceUnit.of("""
                package com.example;

                import com.foo.Bar;
                import java.util.List;

                class Example {
                }
                """));

        assertEquals(Optional.of("com.foo.Bar"), resolver.resolveSimpleName("Bar", unit));
        assertEquals(Optional.of("java.util.List"), resolver.resolveSimpleName("List", unit));
        assertEquals(Optional.empty(), resolver.resolveSimpleName("Missing", unit));
        assertEquals(List.of("Bar", "List"), resolver.importedSimpleNames(unit));
        assertEquals(unit.plainImports(), resolver.plainImports(unit));
    }

    @Test
    void reportsAmbiguousSimpleNames() {
        CompilationUnitModel unit = parser.parse(SourceUnit.of("""
                import com.foo.Bar;
                import com.example.Bar;

                class Example {
                }
                """));

        InvalidImportException failure =
                assertThrows(InvalidImportException.class, () -> resolver.resolveSimpleName("Bar", unit));
        assertTrue(failure.getMessage().contains("Ambiguous import of 'Bar'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("com.foo.Bar"), failure.getMessage());
        assertTrue(failure.getMessage().contains("com.example.Bar"), failure.getMessage());
    }

    @Test
    void cannotResolveWildcardImports() {
        CompilationUnitModel unit = parser.parse(SourceUnit.of("import com.foo.*;\nclass Example {}\n"));
        assertEquals(Optional.empty(), resolver.resolveSimpleName("Bar", unit));
    }

    @Test
    void rejectsAliasesCollidingWithPlainImports() {
        CompilationUnitModel unit = parser.parse(SourceUnit.of("""
                import com.foo.Bar;
                import com.example.Bar as Bar;

                class Example {
                }
                """));

        AliasConflictException failure =
                assertThrows(AliasConflictException.class, () -> resolver.checkAliasConflicts(unit));
        assertTrue(failure.getMessage().contains("Alias 'Bar' conflicts with the import 'com.foo.Bar'"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("line 1"), failure.getMessage());
    }

    @Test
    void rejectsAliasesCollidingWithStaticImports() {
        CompilationUnitModel unit = parser.parse(SourceUnit.of("""
                import static com.foo.Constants.FooBar;
                import com.example.Bar as FooBar;

                class Example {
                }
                """));

        AliasConflictException failure =
                assertThrows(AliasConflictException.class, () -> resolver.checkAliasConflicts(unit));
        assertTrue(failure.getMessage().contains("conflicts with the static import 'com.foo.Constants.FooBar'"),
                failure.getMessage());
    }

    @Test
    void acceptsPlainImportsNextToUnrelatedAliases() {
        CompilationUnitModel unit = parser.parse(SourceUnit.of("""
                import com.foo.Bar;
                import java.util.*;
                import static java.lang.Math.max;
                import com.example.Bar as ExampleBar;

                class Example {
                }
                """));

        assertDoesNotThrow(() -> resolver.checkAliasConflicts(unit));
        assertEquals(List.of("Bar"), resolver.importedSimpleNames(unit));
    }
}
