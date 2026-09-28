package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirements 7 and 8 - aliases used with generics and arrays.
 */
class GenericsAndArraysEndToEndTest {

    private static final String CONTAINERS = """
            import com.foo.Bar as FooBar;
            import com.example.Bar as ExampleBar;

            public class Containers {
                public java.util.List<FooBar> foos = new java.util.ArrayList<>();
                public java.util.Map<FooBar, ExampleBar> pairs = new java.util.HashMap<>();
                public FooBar[] array = new FooBar[2];
                public FooBar[][] matrix = new FooBar[1][1];

                public java.util.List<? extends FooBar> wildcard() { return foos; }

                public <T extends FooBar> String bounded(T value) { return value.describe(); }

                public String fill() {
                    FooBar a = new FooBar("a");
                    FooBar b = new FooBar("b");
                    array[0] = a;
                    array[1] = b;
                    matrix[0][0] = a;
                    foos.add(a);
                    foos.add(b);
                    pairs.put(a, new ExampleBar(1));
                    pairs.put(b, new ExampleBar(2));
                    return a.describe() + "|" + b.describe();
                }

                public String arrayRuntime() {
                    return array.getClass().getName() + ":" + matrix.getClass().getName()
                            + ":" + aa().getClass().getName();
                }

                public FooBar[] build() { return new FooBar[] { new FooBar("built") }; }

                public ExampleBar[] examples() { return new ExampleBar[] { new ExampleBar(9) }; }

                private FooBar[] aa() { return new FooBar[0]; }
            }
            """;

    @Test
    void usesAliasesInGenericsAndArrays() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("Containers", CONTAINERS));
        assertTrue(result.success(), result.problems().toString());

        Class<?> type = result.loadClass("Containers");
        Object instance = TestHarness.newInstance(type);

        assertEquals("com.foo.Bar(a)|com.foo.Bar(b)", TestHarness.invoke(instance, "fill"));
        assertEquals("[Lcom.foo.Bar;:[[Lcom.foo.Bar;:[Lcom.foo.Bar;", TestHarness.invoke(instance, "arrayRuntime"));

        java.util.List<?> foos = (java.util.List<?>) TestHarness.field(instance, "foos");
        assertEquals(2, foos.size());
        assertEquals("com.foo.Bar", foos.get(0).getClass().getName());

        assertEquals("com.foo.Bar(a)", TestHarness.invoke(instance, "bounded", foos.get(0)));
        assertEquals(2, ((java.util.List<?>) TestHarness.invoke(instance, "wildcard")).size());

        java.util.Map<?, ?> pairs = (java.util.Map<?, ?>) TestHarness.field(instance, "pairs");
        assertEquals(2, pairs.size());
        assertEquals("com.example.Bar", pairs.values().iterator().next().getClass().getName());

        Object[] built = (Object[]) TestHarness.invoke(instance, "build");
        assertEquals("com.foo.Bar(built)", TestHarness.invoke(built[0], "describe"));

        Object[] examples = (Object[]) TestHarness.invoke(instance, "examples");
        assertEquals("com.example.Bar(9)", TestHarness.invoke(examples[0], "describe"));
    }

    @Test
    void generatesFullyQualifiedGenericAndArrayTypes() {
        CompilationResult result = TestHarness.compile(BarFixtures.with("Containers", CONTAINERS));
        String generated = result.generatedSources().get("Containers");

        assertTrue(generated.contains("java.util.List<com.foo.Bar> foos = new java.util.ArrayList<>();"), generated);
        assertTrue(generated.contains("java.util.Map<com.foo.Bar, com.example.Bar> pairs"), generated);
        assertTrue(generated.contains("com.foo.Bar[] array = new com.foo.Bar[2];"), generated);
        assertTrue(generated.contains("com.foo.Bar[][] matrix = new com.foo.Bar[1][1];"), generated);
        assertTrue(generated.contains("public java.util.List<? extends com.foo.Bar> wildcard()"), generated);
        assertTrue(generated.contains("public <T extends com.foo.Bar> String bounded(T value)"), generated);
        assertTrue(generated.contains("return new com.example.Bar[] { new com.example.Bar(9) };"), generated);
    }
}
