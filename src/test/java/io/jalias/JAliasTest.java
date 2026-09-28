package io.jalias;

import io.jalias.compiler.CompilationResult;
import io.jalias.compiler.TransformResult;
import io.jalias.model.ImportAlias;
import io.jalias.model.SourceUnit;
import io.jalias.resolver.AliasRegistry;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JAliasTest {

    @Test
    void transformsSourceText() {
        TransformResult result = JAlias.transform("""
                import java.util.Date as UtilDate;

                class Example {
                    UtilDate created = new UtilDate(0L);
                }
                """);

        assertTrue(result.hasChanges());
        assertTrue(result.transformedSource().contains("java.util.Date created = new java.util.Date(0L);"));
        assertTrue(result.transformedSource().contains("// import java.util.Date as UtilDate;"));
        assertEquals(java.util.Map.of("UtilDate", "java.util.Date"), result.aliasMappings());
        assertEquals("Example.java: 1 alias(es), 2 reference(s) rewritten", result.summary());
    }

    @Test
    void compilesUnits() throws Exception {
        CompilationResult result = JAlias.compile(SourceUnit.of("Example", """
                import java.util.Date as UtilDate;

                class Example {
                    UtilDate created = new UtilDate(0L);
                    String describe() { return created.getClass().getName(); }
                }
                """));

        assertTrue(result.success(), result.problems().toString());
        Object instance = TestHarness.newInstance(result.loadClass("Example"));
        assertEquals("java.util.Date", TestHarness.invoke(instance, "describe"));
    }

    @Test
    void compilesToADirectoryWithAClasspath(@TempDir Path output, @TempDir Path library) throws Exception {
        Path fixture = library.resolve("com/foo/Bar.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, """
                package com.foo;

                public class Bar {
                    public String describe() { return "com.foo.Bar"; }
                }
                """);
        CompilationResult compiled = JAlias.compile(
                java.util.List.of(SourceUnit.fromPath(fixture)), library, null);
        assertTrue(compiled.success(), compiled.problems().toString());

        CompilationResult result = JAlias.compile(java.util.List.of(SourceUnit.of("Example", """
                import com.foo.Bar as FooBar;

                public class Example {
                    public String describe() { return new FooBar().describe(); }
                }
                """)), output, java.util.List.of(library.toString()));

        assertTrue(result.success(), result.problems().toString());
        assertTrue(Files.exists(output.resolve("Example.class")));
        Object instance = TestHarness.newInstance(result.loadClass("Example"));
        assertEquals("com.foo.Bar", TestHarness.invoke(instance, "describe"));
    }

    @Test
    void compilesFilesFromDisk(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, """
                import java.util.Date as UtilDate;

                public class Example {
                    public String describe() { return new UtilDate(0L).getClass().getName(); }
                }
                """);

        CompilationResult result = JAlias.compileFiles(file);
        assertTrue(result.success(), result.problems().toString());
        assertEquals("java.util.Date", TestHarness.invoke(TestHarness.newInstance(result.loadClass("Example")),
                "describe"));
    }

    @Test
    void reportsUnreadableFilesAsCompilationFailure() {
        Path missing = Path.of("does", "not", "exist", "Missing.java");
        io.jalias.exceptions.AliasCompilationException failure =
                assertThrows(io.jalias.exceptions.AliasCompilationException.class,
                        () -> JAlias.compileFiles(missing));
        assertTrue(failure.getMessage().contains("Cannot read"), failure.getMessage());
    }

    @Test
    void buildsRegistriesAndResolvers() {
        AliasRegistry registry = JAlias.registry(ImportAlias.of("java.util.Date", "UtilDate"));
        assertEquals("java.util.Date", registry.resolveTypeName("UtilDate"));
        assertEquals(1, JAlias.resolver(registry.declaredAliases()).declaredAliases().size());
        assertFalse(JAlias.resolver(java.util.List.of()).isAlias("UtilDate"));
    }

    @Test
    void exposesAVersion() {
        assertTrue(JAlias.version() != null && !JAlias.version().isBlank());
        assertEquals("0.1.0", JAlias.FALLBACK_VERSION);
    }
}
