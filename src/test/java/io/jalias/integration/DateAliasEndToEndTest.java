package io.jalias.integration;

import io.jalias.compiler.CompilationResult;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The example from the problem statement, compiled and executed: both {@code java.util.Date} and
 * {@code java.sql.Date} are called {@code Date}, and both keep working as their real classes.
 */
class DateAliasEndToEndTest {

    private static final String EXAMPLE = """
            import java.util.Date as UtilDate;
            import java.sql.Date as SqlDate;

            public class Example {
                private final UtilDate createdAt;
                private final SqlDate databaseDate;

                public Example(long millis) {
                    this.createdAt = new UtilDate(millis);
                    this.databaseDate = new SqlDate(millis);
                }

                public UtilDate createdAt() { return createdAt; }

                public SqlDate databaseDate() { return databaseDate; }

                public void test() {
                    UtilDate a = new UtilDate();
                    SqlDate b = new SqlDate(System.currentTimeMillis());
                    if (a.getTime() < 0L || b.getTime() <= 0L) {
                        throw new IllegalStateException("unexpected timestamps");
                    }
                }
            }
            """;

    @Test
    void resolvesBothDateTypesToTheirRealClasses() throws Exception {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Example", EXAMPLE));
        assertTrue(result.success(), result.problems().toString());

        Class<?> type = result.loadClass("Example");
        assertEquals("java.util.Date", type.getDeclaredMethod("createdAt").getReturnType().getName());
        assertEquals("java.sql.Date", type.getDeclaredMethod("databaseDate").getReturnType().getName());

        Object instance = TestHarness.construct(result, "Example", new Class<?>[] {long.class}, 1234L);

        Object utilDate = TestHarness.invoke(instance, "createdAt");
        Object sqlDate = TestHarness.invoke(instance, "databaseDate");
        assertEquals(java.util.Date.class, utilDate.getClass());
        assertEquals(java.sql.Date.class, sqlDate.getClass());
        assertNotEquals(utilDate.getClass(), sqlDate.getClass());
        assertEquals(1234L, ((java.util.Date) utilDate).getTime());
        assertEquals(1234L, ((java.sql.Date) sqlDate).getTime());

        TestHarness.invoke(instance, "test");
    }

    @Test
    void generatesTheDocumentedJava() {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Example", EXAMPLE));
        String generated = result.generatedSources().get("Example");

        assertTrue(generated.contains("// import java.util.Date as UtilDate;"), generated);
        assertTrue(generated.contains("// import java.sql.Date as SqlDate;"), generated);
        assertTrue(generated.contains("private final java.util.Date createdAt;"), generated);
        assertTrue(generated.contains("private final java.sql.Date databaseDate;"), generated);
        assertTrue(generated.contains("this.createdAt = new java.util.Date(millis);"), generated);
        assertTrue(generated.contains("this.databaseDate = new java.sql.Date(millis);"), generated);
        assertTrue(generated.contains("java.util.Date a = new java.util.Date();"), generated);
        assertTrue(generated.contains("java.sql.Date b = new java.sql.Date(System.currentTimeMillis());"),
                generated);
    }

    @Test
    void executesTheFileFromTheExamplesDirectory() {
        java.nio.file.Path example = TestHarness.projectRoot().resolve("examples/date-alias/DateAliasExample.java");
        CompilationResult result = io.jalias.JAlias.compileFiles(example);

        assertTrue(result.success(), result.problems().toString());
        assertEquals(java.util.Map.of("UtilDate", "java.util.Date", "SqlDate", "java.sql.Date"),
                result.aliasMappings());
        assertTrue(result.classes().containsKey("DateAliasExample"));
    }
}
