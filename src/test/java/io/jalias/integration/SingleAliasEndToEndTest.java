package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirement 1 - one aliased import, compiled and executed.
 */
class SingleAliasEndToEndTest {

    @Test
    void runsCodeWrittenWithASingleAlias() throws Exception {
        CompilationResult result = TestHarness.compile(TestHarness.unit("SingleAlias", """
                import java.util.ArrayList as JList;

                public class SingleAlias {
                    private final JList<String> names = new JList<>();

                    public SingleAlias() {
                        names.add("first");
                        names.add("second");
                    }

                    public String describe() {
                        return names.getClass().getName() + " " + names;
                    }
                }
                """));

        assertTrue(result.success(), result.problems().toString());

        String generated = result.generatedSources().get("SingleAlias");
        assertTrue(generated.contains("// import java.util.ArrayList as JList;"), generated);
        assertTrue(generated.contains("private final java.util.ArrayList<String> names = new java.util.ArrayList<>();"),
                generated);

        Object instance = TestHarness.newInstance(result.loadClass("SingleAlias"));
        assertEquals("java.util.ArrayList [first, second]", TestHarness.invoke(instance, "describe"));
    }

    @Test
    void runsCodeThatAliasesALibraryClass() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("AliasedLibrary", """
                import com.foo.Bar as FooBar;

                public class AliasedLibrary {
                    private final FooBar bar = new FooBar("one");

                    public String describe() {
                        return bar.describe() + " / " + FooBar.origin() + " / " + FooBar.CONSTANT;
                    }
                }
                """));

        assertTrue(result.success(), result.problems().toString());

        Object instance = TestHarness.newInstance(result.loadClass("AliasedLibrary"));
        assertEquals("com.foo.Bar(one) / com.foo / foo-constant", TestHarness.invoke(instance, "describe"));
        assertEquals("com.foo.Bar(one)", TestHarness.invoke(TestHarness.field(instance, "bar"), "describe"));
    }

    @Test
    void aliasTargetsAreRealClassesAtRuntime() throws Exception {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Types", """
                import java.util.Date as UtilDate;

                public class Types {
                    public Class<?> aliasClass() { return UtilDate.class; }
                    public Object create() { return new UtilDate(1234L); }
                }
                """));

        assertTrue(result.success(), result.problems().toString());
        Class<?> types = result.loadClass("Types");
        Object instance = TestHarness.newInstance(types);

        assertEquals(java.util.Date.class, TestHarness.invoke(instance, "aliasClass"));
        assertEquals(java.util.Date.class, TestHarness.invoke(instance, "create").getClass());
        assertEquals(java.util.Set.of("Types"), result.classes().keySet());
    }
}
