package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requirement 16 - ordinary Java imports keep working exactly as before, next to aliased ones.
 */
class PlainImportsStillWorkTest {

    @Test
    void supportsSingleWildcardAndStaticImportsNextToAliases() throws Exception {
        CompilationResult result = TestHarness.compile(TestHarness.unit("com.example.app.Plain", """
                package com.example.app;

                import java.util.Date as UtilDate;
                import java.util.List;
                import java.util.ArrayList;
                import java.util.*;
                import static java.lang.Math.max;

                public class Plain {
                    private final List<String> names = new ArrayList<>();
                    private final UtilDate created = new UtilDate(0L);

                    public Plain() {
                        names.add("x");
                    }

                    public String describe() {
                        return names.size() + "/" + max(1, 2) + "/" + created.getTime()
                                + "/" + new HashMap<>().isEmpty();
                    }
                }
                """));

        assertTrue(result.success(), result.problems().toString());

        Object instance = TestHarness.newInstance(result.loadClass("com.example.app.Plain"));
        assertEquals("1/2/0/true", TestHarness.invoke(instance, "describe"));

        String generated = result.generatedSources().get("com.example.app.Plain");
        assertTrue(generated.contains("import java.util.List;"), generated);
        assertTrue(generated.contains("import java.util.*;"), generated);
        assertTrue(generated.contains("import static java.lang.Math.max;"), generated);
        assertTrue(generated.contains("// import java.util.Date as UtilDate;"), generated);
        assertTrue(generated.contains("private final java.util.Date created = new java.util.Date(0L);"), generated);
    }

    @Test
    void supportsFilesWithoutAnyAlias() throws Exception {
        CompilationResult result = TestHarness.compile(TestHarness.unit("com.example.app.NoAlias", """
                package com.example.app;

                import java.util.ArrayList;
                import java.util.List;

                public class NoAlias {
                    public String describe() {
                        List<String> names = new ArrayList<>();
                        names.add("plain");
                        return names.toString();
                    }
                }
                """));

        assertTrue(result.success(), result.problems().toString());
        assertEquals("[plain]", TestHarness.invoke(TestHarness.newInstance(
                result.loadClass("com.example.app.NoAlias")), "describe"));
        assertTrue(result.aliasMappings().isEmpty());

        String generated = result.generatedSources().get("com.example.app.NoAlias");
        assertTrue(generated.contains("import java.util.ArrayList;"), generated);
        assertTrue(generated.contains("import java.util.List;"), generated);
    }

    @Test
    void keepsStringLiteralsAndCommentsIntactInGeneratedSource() {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Comments", """
                import java.util.Date as UtilDate;

                /** Uses UtilDate in its Javadoc. */
                public class Comments {
                    // UtilDate must stay a comment
                    private final String text = "UtilDate is just text";
                    private final UtilDate created = new UtilDate(0L);

                    public String describe() { return text + created.getTime(); }
                }
                """));

        assertTrue(result.success(), result.problems().toString());
        String generated = result.generatedSources().get("Comments");
        assertTrue(generated.contains("/** Uses UtilDate in its Javadoc. */"), generated);
        assertTrue(generated.contains("// UtilDate must stay a comment"), generated);
        assertTrue(generated.contains("\"UtilDate is just text\""), generated);
        assertTrue(generated.contains("private final java.util.Date created = new java.util.Date(0L);"), generated);
    }
}
