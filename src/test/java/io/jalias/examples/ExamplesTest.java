package io.jalias.examples;

import io.jalias.compiler.CompilationResult;
import io.jalias.model.SourceUnit;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles and executes every program under {@code examples/}. Each example checks at runtime that its
 * aliases resolved to the real classes and fails loudly otherwise, so a green test here means the
 * documented examples really work.
 */
class ExamplesTest {

    private String runExample(String mainClass, String... sources) throws Exception {
        List<SourceUnit> units = new ArrayList<>();
        for (String source : sources) {
            Path path = TestHarness.projectRoot().resolve("examples").resolve(source);
            if (java.nio.file.Files.isDirectory(path)) {
                units.addAll(TestHarness.units(TestHarness.javaFiles(path)));
            } else {
                units.addAll(TestHarness.units(List.of(path)));
            }
        }

        CompilationResult result = TestHarness.compile(units);
        assertTrue(result.success(), result.problems().toString());

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            Class<?> type = result.loadClass(mainClass);
            Method main = type.getDeclaredMethod("main", String[].class);
            main.setAccessible(true);
            main.invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void dateAliasExample() throws Exception {
        String output = runExample("DateAliasExample", "date-alias/DateAliasExample.java");
        assertTrue(output.contains("UtilDate -> java.util.Date"), output);
        assertTrue(output.contains("SqlDate  -> java.sql.Date"), output);
        assertTrue(output.contains("DateAliasExample OK"), output);
    }

    @Test
    void sameSimpleNameExample() throws Exception {
        String output = runExample("SameSimpleNameExample",
                "same-simple-name/lib", "same-simple-name/SameSimpleNameExample.java");
        assertTrue(output.contains("com.foo.Bar(first)"), output);
        assertTrue(output.contains("com.example.Bar(7)"), output);
        assertTrue(output.contains("FooBar.origin()     -> com.foo"), output);
        assertTrue(output.contains("ExampleBar.origin() -> com.example"), output);
        assertTrue(output.contains("SameSimpleNameExample OK"), output);
    }

    @Test
    void genericsArraysExample() throws Exception {
        String output = runExample("GenericsArraysExample", "generics-arrays/GenericsArraysExample.java");
        assertTrue(output.contains("entries -> [one=1, two=2]"), output);
        assertTrue(output.contains("index   -> {one=[1], two=[2]}"), output);
        assertTrue(output.contains("sum     -> 1"), output);
        assertTrue(output.contains("GenericsArraysExample OK"), output);
    }

    @Test
    void nestedStaticExample() throws Exception {
        String output = runExample("NestedStaticExample", "nested-static/NestedStaticExample.java");
        assertTrue(output.contains("pattern    -> k=9"), output);
        assertTrue(output.contains("cast       -> k=9"), output);
        assertTrue(output.contains("NestedStaticExample OK"), output);
    }
}
