package io.jalias.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRunnerTest {

    private final CliRunner runner = new CliRunner();

    private record Invocation(int status, String out, String err) {
    }

    private Invocation run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = runner.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Invocation(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Path writeExample(Path directory, String name) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, """
                import java.util.Date as UtilDate;

                public class %s {
                    public static void main(String[] args) {
                        UtilDate created = new UtilDate(0L);
                        if (created.getClass() != java.util.Date.class) {
                            throw new IllegalStateException("alias resolved to " + created.getClass());
                        }
                        System.out.println("hello from " + UtilDate.class.getName());
                    }
                }
                """.formatted(name.replace(".java", "")));
        return file;
    }

    @Test
    void printsUsageAndVersion() {
        Invocation usage = run();
        assertEquals(0, usage.status());
        assertTrue(usage.out().contains("JAlias - import aliasing for Java"), usage.out());
        assertTrue(usage.out().contains("jalias run"), usage.out());

        Invocation version = run("version");
        assertEquals(0, version.status());
        assertTrue(version.out().startsWith("JAlias "), version.out());

        Invocation help = run("help");
        assertEquals(0, help.status());
        assertTrue(help.out().contains("Usage:"), help.out());
    }

    @Test
    void transformsFiles(@TempDir Path directory) throws IOException {
        Path file = writeExample(directory, "Example.java");
        Invocation invocation = run("transform", file.toString());

        assertEquals(0, invocation.status(), invocation.err());
        assertTrue(invocation.out().contains("// import java.util.Date as UtilDate;"), invocation.out());
        assertTrue(invocation.out().contains("java.util.Date created = new java.util.Date(0L);"), invocation.out());
        assertTrue(invocation.out().contains("1 alias(es), 3 reference(s) rewritten"), invocation.out());
    }

    @Test
    void transformsWithoutComments(@TempDir Path directory) throws IOException {
        Path file = writeExample(directory, "Example.java");
        Invocation invocation = run("transform", "--no-comments", file.toString());

        assertEquals(0, invocation.status(), invocation.err());
        assertTrue(invocation.out().contains("java.util.Date created = new java.util.Date(0L);"), invocation.out());
        assertTrue(invocation.out().lines().noneMatch(line -> line.startsWith("// import")), invocation.out());
    }

    @Test
    void writesTransformedFiles(@TempDir Path directory) throws IOException {
        Path file = writeExample(directory, "Example.java");
        Path target = directory.resolve("generated/Example.java");
        Invocation invocation = run("transform", "-o", target.toString(), file.toString());

        assertEquals(0, invocation.status(), invocation.err());
        assertTrue(invocation.out().contains("wrote"), invocation.out());
        String generated = Files.readString(target);
        assertTrue(generated.contains("java.util.Date created = new java.util.Date(0L);"), generated);
    }

    @Test
    void transformsWholeSourceTrees(@TempDir Path directory) throws IOException {
        Path aliasRoot = directory.resolve("src/main/jalias/com/example/app");
        Files.createDirectories(aliasRoot);
        Files.writeString(aliasRoot.resolve("App.java"), """
                package com.example.app;

                import java.util.Date as UtilDate;

                public class App {
                    public String describe() { return new UtilDate(0L).getClass().getName(); }
                }
                """);
        Path outputRoot = directory.resolve("target/generated-sources/jalias");
        Invocation invocation = run("transform", "--source-dir", directory.resolve("src/main/jalias").toString(),
                "-o", outputRoot.toString());

        assertEquals(0, invocation.status(), invocation.err());
        assertTrue(invocation.out().contains("transformed 1 file(s)"), invocation.out());
        assertTrue(invocation.out().contains("UtilDate=java.util.Date"), invocation.out());

        String generated = Files.readString(outputRoot.resolve("com/example/app/App.java"));
        assertTrue(generated.contains("new java.util.Date(0L).getClass().getName()"), generated);
        assertTrue(generated.lines().filter(line -> !line.stripLeading().startsWith("//"))
                .noneMatch(line -> line.contains(" as ")), generated);
    }

    @Test
    void compilesFiles(@TempDir Path directory) throws IOException {
        Path file = writeExample(directory, "Example.java");
        Path output = directory.resolve("classes");
        Invocation invocation = run("compile", "-d", output.toString(), file.toString());

        assertEquals(0, invocation.status(), invocation.err());
        assertTrue(Files.exists(output.resolve("Example.class")));
        assertTrue(invocation.out().contains("compiled 1 class(es)"), invocation.out());
        assertTrue(invocation.out().contains("aliases: {UtilDate=java.util.Date}"), invocation.out());
    }

    @Test
    void compilesInMemoryByDefault(@TempDir Path directory) throws IOException {
        Path file = writeExample(directory, "Example.java");
        Invocation invocation = run("compile", file.toString());

        assertEquals(0, invocation.status(), invocation.err());
        assertTrue(invocation.out().contains("in memory"), invocation.out());
    }

    @Test
    void compilesWholeDirectories(@TempDir Path directory) throws IOException {
        writeExample(directory, "First.java");
        writeExample(directory, "Second.java");
        Invocation invocation = run("compile", directory.toString());

        assertEquals(0, invocation.status(), invocation.err());
        assertTrue(invocation.out().contains("compiled 2 class(es)"), invocation.out());
    }

    @Test
    void reportsCompilationProblems(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("Broken.java");
        Files.writeString(file, """
                import java.util.Date as UtilDate;

                public class Broken {
                    UtilDate created = new UtilDate(0L);
                    void run() { missingMethod(); }
                }
                """);
        Invocation invocation = run("compile", file.toString());

        assertEquals(1, invocation.status());
        assertTrue(invocation.err().contains("missingMethod"), invocation.err());
    }

    @Test
    void reportsAliasProblems(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("Duplicate.java");
        Files.writeString(file, """
                import java.util.Date as UtilDate;
                import java.sql.Date as UtilDate;

                public class Duplicate {
                }
                """);
        Invocation invocation = run("compile", file.toString());

        assertEquals(1, invocation.status());
        assertTrue(invocation.err().contains("already mapped to java.util.Date"), invocation.err());
    }

    @Test
    void rejectsUnknownCommandsAndOptions(@TempDir Path directory) throws IOException {
        Invocation unknownCommand = run("frobnicate");
        assertEquals(2, unknownCommand.status());
        assertTrue(unknownCommand.err().contains("Unknown command 'frobnicate'"), unknownCommand.err());

        Invocation unknownOption = run("compile", "--bogus", "Example.java");
        assertEquals(2, unknownOption.status());
        assertTrue(unknownOption.err().contains("Unknown option '--bogus'"), unknownOption.err());

        Invocation missingValue = run("compile", "-cp");
        assertEquals(2, missingValue.status());
        assertTrue(missingValue.err().contains("-cp needs a value"), missingValue.err());

        Invocation noSources = run("compile", "--no-comments");
        assertEquals(2, noSources.status());
        assertTrue(noSources.err().contains("needs at least one .java file"), noSources.err());

        assertEquals(2, run("run").status());
    }

    @Test
    void skipsMissingFiles(@TempDir Path directory) {
        Invocation invocation = run("compile", directory.resolve("Missing.java").toString());
        assertEquals(2, invocation.status());
        assertTrue(invocation.err().contains("file does not exist"), invocation.err());
    }

    @Test
    void compilesAndRunsPrograms(@TempDir Path directory) throws IOException {
        Path file = writeExample(directory, "Example.java");
        Invocation invocation = run("run", file.toString());

        assertEquals(0, invocation.status(), invocation.err() + invocation.out());
        assertTrue(invocation.out().contains("running Example"), invocation.out());
        assertTrue(invocation.out().contains("hello from java.util.Date"), invocation.out());
    }

    @Test
    void requiresAMainClassForMultipleFiles(@TempDir Path directory) throws IOException {
        writeExample(directory, "First.java");
        writeExample(directory, "Second.java");
        Invocation invocation = run("run", directory.toString());

        assertEquals(2, invocation.status());
        assertTrue(invocation.err().contains("-main <class>"), invocation.err());
    }
}
