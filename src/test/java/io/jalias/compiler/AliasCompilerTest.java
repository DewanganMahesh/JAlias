package io.jalias.compiler;

import io.jalias.exceptions.AliasConflictException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.model.AliasOptions;
import io.jalias.model.SourceUnit;
import io.jalias.resolver.AliasRegistry;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the compiler facade: what is produced for a successful compilation, which failures are
 * diagnostics and which are exceptions.
 */
class AliasCompilerTest {

    private static final String FOO_BAR = """
            package com.foo;

            public class Bar {
                private final String label;
                public Bar(String label) { this.label = label; }
                public Bar() { this("foo"); }
                public String describe() { return "com.foo.Bar(" + label + ")"; }
            }
            """;

    private static final String EXAMPLE_BAR = """
            package com.example;

            public class Bar {
                private final int number;
                public Bar(int number) { this.number = number; }
                public Bar() { this(1); }
                public String describe() { return "com.example.Bar(" + number + ")"; }
            }
            """;

    private static final String SAME_SIMPLE_NAME_EXAMPLE = """
            import com.foo.Bar as FooBar;
            import com.example.Bar as ExampleBar;

            public class Example {
                private final FooBar foo = new FooBar();
                private final ExampleBar example = new ExampleBar();

                public String describe() {
                    return foo.describe() + " | " + example.describe();
                }
            }
            """;

    private List<SourceUnit> sameSimpleNameUnits() {
        return List.of(TestHarness.unit("com.foo.Bar", FOO_BAR),
                TestHarness.unit("com.example.Bar", EXAMPLE_BAR),
                TestHarness.unit("Example", SAME_SIMPLE_NAME_EXAMPLE));
    }

    @Test
    void compilesTwoClassesWithTheSameSimpleName() throws Exception {
        CompilationResult result = TestHarness.compile(sameSimpleNameUnits());

        assertTrue(result.success(), result.problems().toString());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(3, result.generatedSources().size());
        assertEquals(3, result.units().size());
        assertEquals(java.util.Map.of("FooBar", "com.foo.Bar", "ExampleBar", "com.example.Bar"),
                result.aliasMappings());
        assertTrue(result.classes().containsKey("Example"), result.classes().keySet().toString());
        assertTrue(result.classes().containsKey("com.foo.Bar"));

        String generated = result.generatedSources().get("Example");
        assertTrue(generated.contains("// import com.foo.Bar as FooBar;"), generated);
        assertTrue(generated.contains("private final com.foo.Bar foo = new com.foo.Bar();"), generated);
        assertTrue(generated.contains("private final com.example.Bar example = new com.example.Bar();"),
                generated);

        Object example = TestHarness.newInstance(result.loadClass("Example"));
        assertEquals("com.foo.Bar(foo) | com.example.Bar(1)", TestHarness.invoke(example, "describe"));
        assertTrue(result.summary().contains("compiled 3 unit(s)"), result.summary());
    }

    @Test
    void writesClassFilesToADirectory(@TempDir Path output) throws Exception {
        CompilationResult result = TestHarness.compile(sameSimpleNameUnits(), output, null);

        assertTrue(result.success(), result.problems().toString());
        assertTrue(result.classes().isEmpty(), "class files are written to disk, not kept in memory");
        assertTrue(Files.exists(output.resolve("com/foo/Bar.class")));
        assertTrue(Files.exists(output.resolve("com/example/Bar.class")));
        assertTrue(Files.exists(output.resolve("Example.class")));

        Object example = TestHarness.newInstance(result.loadClass("Example"));
        assertEquals("com.foo.Bar(foo) | com.example.Bar(1)", TestHarness.invoke(example, "describe"));
    }

    @Test
    void writesInMemoryClassesToDisk(@TempDir Path output) throws Exception {
        CompilationResult result = TestHarness.compile(sameSimpleNameUnits());
        result.writeClassesTo(output);
        assertTrue(Files.exists(output.resolve("Example.class")));
        assertTrue(Files.exists(output.resolve("com/example/Bar.class")));
    }

    @Test
    void reportsCompilerErrorsAsDiagnostics() {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Example", """
                import java.util.Date as UtilDate;

                class Example {
                    UtilDate created = new UtilDate(0L);
                    void run() { missingMethod(); }
                }
                """));

        assertFalse(result.success());
        assertTrue(result.hasErrors());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("missingMethod")),
                result.problems().toString());
        assertTrue(result.generatedSources().containsKey("Example"));
    }

    @Test
    void reportsSyntaxErrorsAsFailedResult() {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Example", """
                import java.util.Date as UtilDate;

                class Example {
                    UtilDate created = new UtilDate(0L)
                }
                """));

        assertFalse(result.success());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("Cannot parse")),
                result.problems().toString());
    }

    @Test
    void rejectsUnknownImportTargets() {
        InvalidImportException failure = assertThrows(InvalidImportException.class,
                () -> TestHarness.compile(TestHarness.unit("Example", """
                        import com.does.not.Exist as Foo;

                        class Example {
                            Foo value;
                        }
                        """)));
        assertTrue(failure.getMessage().contains("Cannot resolve import 'com.does.not.Exist'"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("for alias 'Foo'"), failure.getMessage());
    }

    @Test
    void rejectsAliasesThatConflictWithVisibleTypes() {
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> TestHarness.compile(TestHarness.unit("Example", """
                        import java.util.Date as String;

                        class Example {
                        }
                        """)));
        assertTrue(failure.getMessage().contains("Alias 'String' conflicts with existing type"),
                failure.getMessage());
    }

    @Test
    void skipsVisibleTypeChecksWhenDisabled() {
        CompilationResult result = new AliasCompiler(AliasOptions.defaults().withCheckVisibleTypeConflicts(false))
                .compile(AliasCompilationRequest.builder()
                        .sources(TestHarness.unit("Example", "import java.util.Date as String;\n\nclass Example {\n}\n"))
                        .build());
        assertTrue(result.success(), result.problems().toString());
    }

    @Test
    void skipsImportTargetVerificationWhenDisabled() {
        AliasOptions options = AliasOptions.defaults().withVerifyImportTargets(false);
        CompilationResult result = new AliasCompiler(options).compile(AliasCompilationRequest.builder()
                .sources(TestHarness.unit("Example", """
                        import com.does.not.Exist as Foo;

                        class Example {
                            Foo value;
                        }
                        """))
                .build());

        assertFalse(result.success());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("com.does.not")),
                result.problems().toString());
    }

    @Test
    void rejectsAliasConflictsWithPlainImports() {
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> TestHarness.compile(TestHarness.unit("Example", """
                        import java.util.Date;
                        import java.sql.Date as Date;

                        class Example {
                        }
                        """)));
        assertTrue(failure.getMessage().contains("Alias 'Date' conflicts with the import 'java.util.Date'"),
                failure.getMessage());
    }

    @Test
    void reportsDuplicateAliases() {
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> TestHarness.compile(TestHarness.unit("Example", """
                        import java.util.Date as UtilDate;
                        import java.sql.Date as UtilDate;

                        class Example {
                        }
                        """)));
        assertEquals("Alias 'UtilDate' is already mapped to java.util.Date", failure.getMessage());
    }

    @Test
    void buildsRegistriesFromUnits() {
        AliasRegistry registry = new AliasCompiler().registry(TestHarness.unit("Example", """
                import java.util.Date as UtilDate;
                import java.sql.Date as SqlDate;

                class Example {
                }
                """));
        assertEquals(2, registry.size());
        assertTrue(registry.isAlias("UtilDate"));

        assertThrows(IllegalArgumentException.class,
                () -> new AliasCompiler().compile(AliasCompilationRequest.builder().build()));
    }

    @Test
    void parsesAndTransformsSingleUnits() {
        AliasCompiler compiler = new AliasCompiler();
        SourceUnit unit = TestHarness.unit("Example", """
                import java.util.Date as UtilDate;

                class Example {
                    UtilDate created;
                }
                """);

        assertEquals("UtilDate", compiler.parse(unit).aliases().get(0).alias());
        assertEquals(1, compiler.transform(unit).replacements());
        assertEquals(java.util.Map.of("UtilDate", "java.util.Date"), compiler.transform(unit).aliasMappings());
    }

    @Test
    void exposesTheConfiguredOptions() {
        AliasOptions options = AliasOptions.defaults().withEmitAliasComments(false);
        assertEquals(options, new AliasCompiler(options).options());
        assertEquals(AliasOptions.defaults(), new AliasCompiler().options());
        assertTrue(new AliasCompiler().parser() != null);
    }
}
