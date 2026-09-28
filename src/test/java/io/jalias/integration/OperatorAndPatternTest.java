package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirements 12 and 13 - {@code instanceof} (including patterns) and casts with aliased types.
 */
class OperatorAndPatternTest {

    private static final String OPERATORS = """
            import com.foo.Bar as FooBar;
            import com.example.Bar as ExampleBar;

            public class Operators {
                public String classic(Object value) {
                    if (value instanceof FooBar) {
                        return "foo";
                    }
                    if (value instanceof ExampleBar) {
                        return "example";
                    }
                    return "other";
                }

                public String patterns(Object value) {
                    if (value instanceof FooBar foo) {
                        return "foo:" + foo.label();
                    }
                    return value instanceof ExampleBar example ? "example:" + example.number() : "other";
                }

                public String switched(Object value) {
                    return switch (value) {
                        case FooBar foo -> "foo:" + foo.label();
                        case ExampleBar example -> "example:" + example.number();
                        default -> "other";
                    };
                }

                public FooBar cast(Object value) { return (FooBar) value; }

                public ExampleBar castToExample(Object value) { return (ExampleBar) value; }

                public Class<?> literal() { return FooBar.class; }

                public boolean sameType() { return FooBar.class == com.foo.Bar.class; }

                public String typeName() { return FooBar.class.getName() + "/" + ExampleBar.class.getSimpleName(); }
            }
            """;

    @Test
    void worksInInstanceofCastsAndPatterns() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("Operators", OPERATORS));
        assertTrue(result.success(), result.problems().toString());

        Object instance = TestHarness.newInstance(result.loadClass("Operators"));
        Object foo = TestHarness.construct(result, "com.foo.Bar", new Class<?>[] {String.class}, "value");
        Object example = TestHarness.construct(result, "com.example.Bar", new Class<?>[] {int.class}, 3);

        assertEquals("foo", TestHarness.invoke(instance, "classic", foo));
        assertEquals("example", TestHarness.invoke(instance, "classic", example));
        assertEquals("other", TestHarness.invoke(instance, "classic", "some string"));

        assertEquals("foo:value", TestHarness.invoke(instance, "patterns", foo));
        assertEquals("example:3", TestHarness.invoke(instance, "patterns", example));
        assertEquals("other", TestHarness.invoke(instance, "patterns", "some string"));

        assertEquals("foo:value", TestHarness.invoke(instance, "switched", foo));
        assertEquals("example:3", TestHarness.invoke(instance, "switched", example));
        assertEquals("other", TestHarness.invoke(instance, "switched", "some string"));

        assertEquals(foo, TestHarness.invoke(instance, "cast", foo));
        assertEquals(example, TestHarness.invoke(instance, "castToExample", example));

        java.lang.reflect.Method castToExample =
                instance.getClass().getDeclaredMethod("castToExample", Object.class);
        castToExample.setAccessible(true);
        java.lang.reflect.InvocationTargetException failure =
                assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> castToExample.invoke(instance, foo));
        assertTrue(failure.getCause() instanceof ClassCastException, String.valueOf(failure.getCause()));

        assertEquals(result.loadClass("com.foo.Bar"), TestHarness.invoke(instance, "literal"));
        assertTrue((Boolean) TestHarness.invoke(instance, "sameType"));
        assertEquals("com.foo.Bar/Bar", TestHarness.invoke(instance, "typeName"));
    }

    @Test
    void generatesFullyQualifiedOperators() {
        CompilationResult result = TestHarness.compile(BarFixtures.with("Operators", OPERATORS));
        String generated = result.generatedSources().get("Operators");

        assertTrue(generated.contains("value instanceof com.foo.Bar foo"), generated);
        assertTrue(generated.contains("case com.foo.Bar foo ->"), generated);
        assertTrue(generated.contains("return (com.foo.Bar) value;"), generated);
        assertTrue(generated.contains("return com.foo.Bar.class;"), generated);
        assertTrue(generated.contains("com.foo.Bar.class == com.foo.Bar.class"), generated);
    }
}
