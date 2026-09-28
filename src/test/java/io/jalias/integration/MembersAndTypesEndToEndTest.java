package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirements 4, 5, 6, 11 and the "preserve normal Java behavior" list: aliases in fields, method
 * parameters, return types, constructors, inheritance, interfaces, exceptions, annotations, casts,
 * {@code instanceof} and class literals.
 */
class MembersAndTypesEndToEndTest {

    private static final String MEMBERS = """
            import com.foo.Bar as FooBar;
            import com.example.Bar as ExampleBar;
            import java.lang.Deprecated as Legacy;

            public class Members {
                private FooBar foo = new FooBar("field");
                private ExampleBar example = new ExampleBar(11);

                public Members() { }

                public Members(FooBar foo, ExampleBar example) {
                    this.foo = foo;
                    this.example = example;
                }

                public FooBar foo() { return foo; }

                public ExampleBar example() { return example; }

                public String describe(FooBar first, ExampleBar second) {
                    return first.describe() + " + " + second.describe();
                }

                public String statics() { return FooBar.origin() + "/" + ExampleBar.origin(); }

                public String constants() { return FooBar.CONSTANT + "/" + ExampleBar.CONSTANT; }

                public String nested() { return new FooBar.Nested().describe() + " + " + new ExampleBar.Nested().describe(); }

                public boolean isFoo(Object value) { return value instanceof FooBar; }

                public FooBar cast(Object value) { return (FooBar) value; }

                public Class<?> literal() { return FooBar.class; }

                public java.util.function.Supplier<FooBar> supplier() { return FooBar::new; }

                public java.util.function.Function<FooBar, String> mapper() { return FooBar::describe; }

                public String throwsAndCatches() {
                    try {
                        throw new IllegalStateException("boom");
                    } catch (IllegalStateException e) {
                        return "caught:" + e.getMessage();
                    }
                }

                @Legacy
                public String legacy() { return "legacy"; }

                public String compare() {
                    return Integer.toString(foo.compareTo(new FooBar("field")));
                }
            }
            """;

    @Test
    void runsFieldsParametersReturnTypesAndConstructors() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("Members", MEMBERS));
        assertTrue(result.success(), result.problems().toString());

        Class<?> type = result.loadClass("Members");
        Object instance = TestHarness.newInstance(type);

        assertEquals("com.foo.Bar", type.getDeclaredMethod("foo").getReturnType().getName());
        assertEquals("com.example.Bar", type.getDeclaredMethod("example").getReturnType().getName());
        assertEquals("com.foo.Bar", type.getDeclaredField("foo").getType().getName());
        assertEquals("com.example.Bar", type.getDeclaredField("example").getType().getName());

        Object foo = TestHarness.invoke(instance, "foo");
        Object example = TestHarness.invoke(instance, "example");
        assertEquals("com.foo.Bar(field) + com.example.Bar(11)",
                TestHarness.invoke(instance, "describe", foo, example));

        Object viaConstructor = TestHarness.construct(result, "Members",
                new Class<?>[] {result.loadClass("com.foo.Bar"), result.loadClass("com.example.Bar")},
                TestHarness.construct(result, "com.foo.Bar", new Class<?>[] {String.class}, "via-ctor"),
                TestHarness.construct(result, "com.example.Bar", new Class<?>[] {int.class}, 5));
        assertEquals("com.foo.Bar(via-ctor) + com.example.Bar(5)",
                TestHarness.invoke(viaConstructor, "describe", TestHarness.invoke(viaConstructor, "foo"),
                        TestHarness.invoke(viaConstructor, "example")));
    }

    @Test
    void runsStaticMembersNestedTypesAndBehaviour() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("Members", MEMBERS));
        assertTrue(result.success(), result.problems().toString());
        Object instance = TestHarness.newInstance(result.loadClass("Members"));
        Object foo = TestHarness.invoke(instance, "foo");
        Object example = TestHarness.invoke(instance, "example");

        assertEquals("com.foo/com.example", TestHarness.invoke(instance, "statics"));
        assertEquals("foo-constant/42", TestHarness.invoke(instance, "constants"));
        assertEquals("com.foo.Bar.Nested + com.example.Bar.Nested", TestHarness.invoke(instance, "nested"));
        assertEquals("caught:boom", TestHarness.invoke(instance, "throwsAndCatches"));
        assertEquals("legacy", TestHarness.invoke(instance, "legacy"));
        assertEquals("0", TestHarness.invoke(instance, "compare"));

        assertTrue((Boolean) TestHarness.invoke(instance, "isFoo", foo));
        assertFalse((Boolean) TestHarness.invoke(instance, "isFoo", example));
        assertEquals(foo, TestHarness.invoke(instance, "cast", foo));
        assertEquals(result.loadClass("com.foo.Bar"), TestHarness.invoke(instance, "literal"));
    }

    @Test
    void runsMethodReferencesAndLambdas() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("Members", MEMBERS));
        Object instance = TestHarness.newInstance(result.loadClass("Members"));
        Object foo = TestHarness.invoke(instance, "foo");

        java.util.function.Supplier<?> supplier = (java.util.function.Supplier<?>) TestHarness.invoke(instance,
                "supplier");
        assertEquals("com.foo.Bar(foo-default)", TestHarness.invoke(supplier.get(), "describe"));

        @SuppressWarnings("unchecked")
        java.util.function.Function<Object, String> mapper =
                (java.util.function.Function<Object, String>) TestHarness.invoke(instance, "mapper");
        assertEquals("com.foo.Bar(field)", mapper.apply(foo));
    }

    @Test
    void runsAliasedInheritanceAndInterfaces() throws Exception {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Hierarchy", """
                import java.util.AbstractList as Base;
                import java.io.Closeable as Closer;

                public class Hierarchy extends Base<String> implements Closer {
                    private final java.util.List<String> values = new java.util.ArrayList<>();
                    private boolean closed;

                    public String get(int index) { return values.get(index); }

                    public int size() { return values.size(); }

                    public boolean add(String value) { return values.add(value); }

                    public void close() { closed = true; }

                    public boolean isClosed() { return closed; }
                }
                """));

        assertTrue(result.success(), result.problems().toString());
        Class<?> type = result.loadClass("Hierarchy");
        assertEquals("java.util.AbstractList", type.getSuperclass().getName());
        assertTrue(java.io.Closeable.class.isAssignableFrom(type));

        Object instance = TestHarness.newInstance(type);
        assertTrue((Boolean) TestHarness.invoke(instance, "add", "first"));
        assertEquals(1, TestHarness.invoke(instance, "size"));
        assertEquals("first", TestHarness.invoke(instance, "get", 0));
        TestHarness.invoke(instance, "close");
        assertTrue((Boolean) TestHarness.invoke(instance, "isClosed"));
    }
}
