package io.jalias.compiler;

import io.jalias.model.AliasOptions;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tree transformation is what build integrations use: alias sources live outside the compiled source
 * directory and are turned into generated sources.
 */
class SourceTreeTransformerTest {

    private Path writeAliasSource(Path aliasRoot) throws IOException {
        Path file = aliasRoot.resolve("com/example/app/Aliased.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package com.example.app;

                import java.util.Date as UtilDate;
                import java.sql.Date as SqlDate;

                public class Aliased {
                    public String describe() {
                        UtilDate util = new UtilDate(0L);
                        SqlDate sql = new SqlDate(0L);
                        return util.getClass().getName() + "/" + sql.getClass().getName();
                    }
                }
                """);
        return file;
    }

    @Test
    void transformsATreeIntoGeneratedSources(@TempDir Path directory) throws IOException {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path plain = aliasRoot.resolve("com/example/app/Plain.java");
        Files.writeString(plain, "package com.example.app;\n\npublic class Plain {\n}\n");
        Path outputRoot = directory.resolve("target/generated-sources/jalias");

        SourceTreeTransformer.Result result = new SourceTreeTransformer().transform(aliasRoot, outputRoot);

        assertEquals(2, result.count());
        assertEquals(1, result.withAliases().size());
        assertEquals(Map.of("UtilDate", "java.util.Date", "SqlDate", "java.sql.Date"), result.aliasMappings());
        assertTrue(result.summary().contains("transformed 2 file(s)"), result.summary());

        String generated = Files.readString(outputRoot.resolve("com/example/app/Aliased.java"));
        assertTrue(generated.contains("// import java.util.Date as UtilDate;"), generated);
        assertTrue(generated.contains("java.util.Date util = new java.util.Date(0L);"), generated);
        assertTrue(generated.lines().filter(line -> !line.stripLeading().startsWith("//"))
                .noneMatch(line -> line.contains(" as ")), generated);

        assertEquals(Files.readString(plain), Files.readString(outputRoot.resolve("com/example/app/Plain.java")));
        assertFalse(result.withAliases().get(0).target().toString().contains("Plain"));

        // the generated tree compiles as ordinary Java
        CompilationResult compiled = TestHarness.compile(TestHarness.units(TestHarness.javaFiles(outputRoot)));
        assertTrue(compiled.success(), compiled.problems().toString());
    }

    @Test
    void keepsTheAliasCommentsOnlyWhenConfigured(@TempDir Path directory) throws IOException {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path outputRoot = directory.resolve("out");

        new SourceTreeTransformer(AliasOptions.defaults().withEmitAliasComments(false))
                .transform(aliasRoot, outputRoot);

        String generated = Files.readString(outputRoot.resolve("com/example/app/Aliased.java"));
        assertFalse(generated.contains("// import"), generated);
        assertTrue(generated.contains("java.sql.Date sql = new java.sql.Date(0L);"), generated);
    }

    @Test
    void reportsAMissingSourceDirectory(@TempDir Path directory) {
        Path missing = directory.resolve("nope");
        IOException failure = assertThrows(IOException.class,
                () -> new SourceTreeTransformer().transform(missing, directory.resolve("out")));
        assertTrue(failure.getMessage().contains("does not exist"), failure.getMessage());
    }

    @Test
    void handlesAnEmptyTree(@TempDir Path directory) throws IOException {
        Path aliasRoot = Files.createDirectories(directory.resolve("src/main/jalias"));
        SourceTreeTransformer.Result result = new SourceTreeTransformer()
                .transform(aliasRoot, directory.resolve("out"));

        assertEquals(0, result.count());
        assertTrue(result.withAliases().isEmpty());
        assertTrue(result.aliasMappings().isEmpty());
    }

    @Test
    void listsJavaFilesBelowARoot(@TempDir Path directory) throws IOException {
        writeAliasSource(directory);
        Files.writeString(directory.resolve("README.md"), "not java");

        assertEquals(1, SourceTreeTransformer.javaFiles(directory).size());
        assertTrue(SourceTreeTransformer.javaFiles(directory).get(0).toString().endsWith("Aliased.java"));
    }
}
