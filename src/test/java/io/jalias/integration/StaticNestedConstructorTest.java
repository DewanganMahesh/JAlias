package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirements 9, 10 and 11 - static members, nested classes and constructors reached through aliases.
 */
class StaticNestedConstructorTest {

    private static final String STATIC_AND_NESTED = """
            import com.foo.Bar as FooBar;
            import com.example.Bar as ExampleBar;

            public class StaticAndNested {
                private final FooBar.Nested fooNested = new FooBar.Nested();
                private FooBar.Nested[] nestedArray = new FooBar.Nested[1];

                public String statics() {
                    return FooBar.origin() + "|" + ExampleBar.origin()
                            + "|" + FooBar.CONSTANT + "|" + ExampleBar.CONSTANT;
                }

                public String nestedTypes() {
                    FooBar.Nested fromFoo = new FooBar.Nested();
                    ExampleBar.Nested fromExample = new ExampleBar.Nested();
                    nestedArray[0] = fromFoo;
                    return fromFoo.describe() + "|" + fromExample.describe() + "|" + nestedArray[0].describe();
                }

                public String constructors() {
                    FooBar defaultFoo = new FooBar();
                    FooBar labelledFoo = new FooBar("labelled");
                    ExampleBar defaultExample = new ExampleBar();
                    ExampleBar numberedExample = new ExampleBar(5);
                    return defaultFoo.describe() + "|" + labelledFoo.describe()
                            + "|" + defaultExample.describe() + "|" + numberedExample.describe();
                }

                public String nestedThroughAlias() {
                    return fooNested.describe();
                }

                public boolean nestedInstanceOf(Object value) {
                    return value instanceof FooBar.Nested;
                }
            }
            """;

    @Test
    void reachesStaticMembersThroughAliases() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("StaticAndNested", STATIC_AND_NESTED));
        assertTrue(result.success(), result.problems().toString());

        Object instance = TestHarness.newInstance(result.loadClass("StaticAndNested"));
        assertEquals("com.foo|com.example|foo-constant|42", TestHarness.invoke(instance, "statics"));
    }

    @Test
    void instantiatesNestedClassesOfAliasedTypes() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("StaticAndNested", STATIC_AND_NESTED));
        Object instance = TestHarness.newInstance(result.loadClass("StaticAndNested"));

        assertEquals("com.foo.Bar.Nested|com.example.Bar.Nested|com.foo.Bar.Nested",
                TestHarness.invoke(instance, "nestedTypes"));
        assertEquals("com.foo.Bar.Nested", TestHarness.invoke(instance, "nestedThroughAlias"));

        Class<?> nestedType = result.loadClass("com.foo.Bar$Nested");
        assertTrue(nestedType.getName().equals("com.foo.Bar$Nested"));
        Object nested = TestHarness.newInstance(nestedType);
        assertTrue((Boolean) TestHarness.invoke(instance, "nestedInstanceOf", nested));
    }

    @Test
    void invokesConstructorsReachedThroughAliases() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("StaticAndNested", STATIC_AND_NESTED));
        Object instance = TestHarness.newInstance(result.loadClass("StaticAndNested"));

        assertEquals("com.foo.Bar(foo-default)|com.foo.Bar(labelled)|com.example.Bar(0)|com.example.Bar(5)",
                TestHarness.invoke(instance, "constructors"));

        Object created = TestHarness.construct(result, "com.foo.Bar", new Class<?>[] {String.class}, "direct");
        assertEquals("com.foo.Bar(direct)", TestHarness.invoke(created, "describe"));
    }

    @Test
    void generatesFullyQualifiedNestedTypes() {
        CompilationResult result = TestHarness.compile(BarFixtures.with("StaticAndNested", STATIC_AND_NESTED));
        String generated = result.generatedSources().get("StaticAndNested");

        assertTrue(generated.contains("private final com.foo.Bar.Nested fooNested = new com.foo.Bar.Nested();"),
                generated);
        assertTrue(generated.contains("com.foo.Bar.Nested[] nestedArray = new com.foo.Bar.Nested[1];"), generated);
        assertTrue(generated.contains("value instanceof com.foo.Bar.Nested"), generated);
        assertTrue(generated.contains("com.foo.Bar.origin()"), generated);
        assertTrue(generated.contains("com.example.Bar.CONSTANT"), generated);
    }
}
