package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirement 3 - several aliases in one file, including two aliases for the same simple name.
 */
class MultipleAliasesEndToEndTest {

    private static final String EXAMPLE = """
            import com.foo.Bar as FooBar;
            import com.example.Bar as ExampleBar;
            import java.util.ArrayList as JList;
            import java.util.TreeMap as JMap;
            import java.util.Date as UtilDate;

            public class MultipleAliases {
                private final JList<String> names = new JList<>();
                private final JMap<String, FooBar> bars = new JMap<>();
                private final UtilDate created = new UtilDate(0L);

                public MultipleAliases() {
                    names.add("alpha");
                    bars.put("foo", new FooBar("foo"));
                    bars.put("example", new FooBar("example"));
                }

                public String describe() {
                    return names.getClass().getSimpleName() + names
                            + " " + bars.getClass().getSimpleName() + bars.keySet()
                            + " " + created.getClass().getName() + "@" + created.getTime()
                            + " " + new ExampleBar(3).describe();
                }
            }
            """;

    @Test
    void resolvesEveryAliasIndependently() throws Exception {
        CompilationResult result = TestHarness.compile(BarFixtures.with("MultipleAliases", EXAMPLE));
        assertTrue(result.success(), result.problems().toString());

        assertEquals(java.util.Map.of("FooBar", "com.foo.Bar", "ExampleBar", "com.example.Bar",
                        "JList", "java.util.ArrayList", "JMap", "java.util.TreeMap", "UtilDate", "java.util.Date"),
                result.aliasMappings());

        Object instance = TestHarness.newInstance(result.loadClass("MultipleAliases"));
        assertEquals("ArrayList[alpha] TreeMap[example, foo] java.util.Date@0 com.example.Bar(3)",
                TestHarness.invoke(instance, "describe"));

        assertEquals("java.util.ArrayList", TestHarness.field(instance, "names").getClass().getName());
        assertEquals("java.util.TreeMap", TestHarness.field(instance, "bars").getClass().getName());
        assertEquals("java.util.Date", TestHarness.field(instance, "created").getClass().getName());
    }

    @Test
    void generatesOneCommentPerAlias() {
        CompilationResult result = TestHarness.compile(BarFixtures.with("MultipleAliases", EXAMPLE));
        String generated = result.generatedSources().get("MultipleAliases");

        assertTrue(generated.contains("// import com.foo.Bar as FooBar;"), generated);
        assertTrue(generated.contains("// import com.example.Bar as ExampleBar;"), generated);
        assertTrue(generated.contains("// import java.util.ArrayList as JList;"), generated);
        assertTrue(generated.contains("// import java.util.TreeMap as JMap;"), generated);
        assertTrue(generated.contains("// import java.util.Date as UtilDate;"), generated);
        assertTrue(generated.contains("private final java.util.ArrayList<String> names = new java.util.ArrayList<>();"),
                generated);
    }
}
