package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirement 2 - two classes with the same simple name, used at the same time, without ambiguity.
 */
class SameSimpleNameEndToEndTest {

    private static final String EXAMPLE = """
            import com.foo.Bar as FooBar;
            import com.example.Bar as ExampleBar;

            public class SameSimpleName {
                private final FooBar foo = new FooBar("foo-bar");
                private final ExampleBar example = new ExampleBar(7);

                public String describe() {
                    return foo.describe() + " | " + example.describe();
                }

                public String bothStatic() {
                    return FooBar.origin() + " + " + ExampleBar.origin();
                }

                public String bothConstructors() {
                    return new FooBar().describe() + " | " + new ExampleBar().describe();
                }

                public FooBar asFoo(ExampleBar other) {
                    return new FooBar(other.number() + "-converted");
                }
            }
            """;

    @Test
    void keepsBothTypesApart() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("SameSimpleName", EXAMPLE));
        assertTrue(result.success(), result.problems().toString());

        Class<?> type = result.loadClass("SameSimpleName");
        Object instance = TestHarness.newInstance(type);

        assertEquals("com.foo.Bar(foo-bar) | com.example.Bar(7)", TestHarness.invoke(instance, "describe"));
        assertEquals("com.foo + com.example", TestHarness.invoke(instance, "bothStatic"));
        assertEquals("com.foo.Bar(foo-default) | com.example.Bar(0)", TestHarness.invoke(instance, "bothConstructors"));

        assertEquals("com.foo.Bar", TestHarness.field(instance, "foo").getClass().getName());
        assertEquals("com.example.Bar", TestHarness.field(instance, "example").getClass().getName());
        assertNotEquals(TestHarness.field(instance, "foo").getClass(),
                TestHarness.field(instance, "example").getClass());

        assertEquals("com.foo.Bar", type.getDeclaredField("foo").getType().getName());
        assertEquals("com.example.Bar", type.getDeclaredField("example").getType().getName());

        Object converted = TestHarness.invoke(instance, "asFoo", TestHarness.field(instance, "example"));
        assertEquals("com.foo.Bar(7-converted)", TestHarness.invoke(converted, "describe"));
    }

    @Test
    void generatesFullyQualifiedNamesForBothTypes() {
        CompilationResult result = TestHarness.compile(BarFixtures.with("SameSimpleName", EXAMPLE));
        String generated = result.generatedSources().get("SameSimpleName");

        assertTrue(generated.contains("private final com.foo.Bar foo = new com.foo.Bar(\"foo-bar\");"), generated);
        assertTrue(generated.contains("private final com.example.Bar example = new com.example.Bar(7);"), generated);
        assertTrue(generated.contains("public com.foo.Bar asFoo(com.example.Bar other)"), generated);
        assertTrue(generated.contains("// import com.foo.Bar as FooBar;"), generated);
        assertTrue(generated.contains("// import com.example.Bar as ExampleBar;"), generated);
    }

    @Test
    void aliasesAreScopedToTheirOwnFile() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with(
                io.jalias.model.SourceUnit.of("First", """
                        import com.foo.Bar as BarAlias;

                        public class First {
                            public String describe() { return new BarAlias("first").describe(); }
                        }
                        """),
                io.jalias.model.SourceUnit.of("Second", """
                        import com.example.Bar as BarAlias;

                        public class Second {
                            public String describe() { return new BarAlias(2).describe(); }
                        }
                        """)));

        assertTrue(result.success(), result.problems().toString());
        assertEquals("com.foo.Bar(first)",
                TestHarness.invoke(TestHarness.newInstance(result.loadClass("First")), "describe"));
        assertEquals("com.example.Bar(2)",
                TestHarness.invoke(TestHarness.newInstance(result.loadClass("Second")), "describe"));
    }
}
