package io.jalias.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformMojoTest {

    private static final String APP = """
            package com.example.app;

            import java.util.Date as UtilDate;
            import java.sql.Date as SqlDate;

            public class App {
                public String describe() {
                    UtilDate util = new UtilDate(0L);
                    SqlDate sql = new SqlDate(0L);
                    return util.getClass().getName() + "/" + sql.getClass().getName();
                }
            }
            """;

    private Path writeAliasSource(Path aliasRoot) throws IOException {
        Path file = aliasRoot.resolve("com/example/app/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, APP);
        Path plain = aliasRoot.resolve("com/example/app/Plain.java");
        Files.writeString(plain, """
                package com.example.app;

                public class Plain {
                    public String describe() { return "plain"; }
                }
                """);
        return file;
    }

    private TransformMojo mojo(Path aliasRoot, Path outputRoot, MavenProject project) {
        TransformMojo mojo = new TransformMojo();
        mojo.setSourceDirectory(aliasRoot.toFile());
        mojo.setOutputDirectory(outputRoot.toFile());
        mojo.setProject(project);
        mojo.setClasspathElements(List.of());
        return mojo;
    }

    @Test
    void transformsTheAliasTreeAndRegistersTheSourceRoot(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path outputRoot = directory.resolve("target/generated-sources/jalias");

        MavenProject project = new MavenProject();
        project.addCompileSourceRoot(directory.resolve("src/main/java").toString());
        TransformMojo mojo = mojo(aliasRoot, outputRoot, project);

        mojo.execute();

        Path generated = outputRoot.resolve("com/example/app/App.java");
        assertTrue(Files.exists(generated), "the alias source must be transformed: " + generated);
        assertTrue(Files.exists(outputRoot.resolve("com/example/app/Plain.java")),
                "files without aliases are copied as well");

        String source = Files.readString(generated);
        assertTrue(source.contains("// import java.util.Date as UtilDate;"), source);
        assertTrue(source.contains("java.util.Date util = new java.util.Date(0L);"), source);
        assertTrue(source.contains("java.sql.Date sql = new java.sql.Date(0L);"), source);
        assertTrue(source.lines().filter(line -> !line.stripLeading().startsWith("//"))
                .noneMatch(line -> line.contains(" as ")), source);

        assertTrue(project.getCompileSourceRoots().contains(outputRoot.toString()),
                project.getCompileSourceRoots().toString());
    }

    @Test
    void rejectsAnAliasDirectoryInsideACompiledSourceRoot(@TempDir Path directory) throws Exception {
        Path sourceRoot = directory.resolve("src/main/java");
        writeAliasSource(sourceRoot);

        MavenProject project = new MavenProject();
        project.addCompileSourceRoot(sourceRoot.toString());
        TransformMojo mojo = mojo(sourceRoot, directory.resolve("target/generated-sources/jalias"), project);

        MojoFailureException failure = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(failure.getMessage().contains("is inside the compiled source root"), failure.getMessage());
        assertTrue(failure.getMessage().contains("javac cannot parse 'import x as y;'"), failure.getMessage());
    }

    @Test
    void reportsAliasSyntaxInsideACompiledSourceRoot(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path compiledRoot = directory.resolve("src/main/java");
        Path misplaced = compiledRoot.resolve("com/example/demo/Misplaced.java");
        Files.createDirectories(misplaced.getParent());
        Files.writeString(misplaced, """
                package com.example.demo;

                import java.util.Date as UtilDate;

                public class Misplaced {
                    public String describe() { return new UtilDate(0L).getClass().getName(); }
                }
                """);

        MavenProject project = new MavenProject();
        project.addCompileSourceRoot(compiledRoot.toString());
        TransformMojo mojo = mojo(aliasRoot, directory.resolve("target/generated-sources/jalias"), project);

        MojoFailureException failure = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(failure.getMessage().contains("uses alias syntax but is inside the compiled source root"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("';' expected"), failure.getMessage());
        assertTrue(failure.getMessage().contains(aliasRoot.toString()), failure.getMessage());
    }

    @Test
    void acceptsCompiledSourcesThatOnlyMentionAliasSyntaxInComments(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path compiledRoot = directory.resolve("src/main/java");
        Path plain = compiledRoot.resolve("com/example/demo/Plain.java");
        Files.createDirectories(plain.getParent());
        Files.writeString(plain, """
                package com.example.demo;

                /** Reminder: an alias would read "import java.util.Date as UtilDate;". */
                public class Plain {
                    // import java.util.Date as UtilDate;
                    public String describe() { return "plain"; }
                }
                """);

        MavenProject project = new MavenProject();
        project.addCompileSourceRoot(compiledRoot.toString());
        TransformMojo mojo = mojo(aliasRoot, directory.resolve("target/generated-sources/jalias"), project);

        mojo.execute();

        assertTrue(Files.exists(directory.resolve("target/generated-sources/jalias/com/example/app/App.java")));
    }

    @Test
    void doesNothingWhenThereAreNoAliasSources(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        Path outputRoot = directory.resolve("target/generated-sources/jalias");
        TransformMojo mojo = mojo(aliasRoot, outputRoot, new MavenProject());

        mojo.execute();

        assertFalse(Files.exists(outputRoot), "nothing is generated when there is nothing to transform");
    }

    @Test
    void skipsWhenConfigured(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path outputRoot = directory.resolve("target/generated-sources/jalias");
        TransformMojo mojo = mojo(aliasRoot, outputRoot, new MavenProject());
        mojo.setSkip(true);

        mojo.execute();

        assertFalse(Files.exists(outputRoot), "a skipped execution writes nothing");
    }

    @Test
    void doesNotRegisterTheSourceRootWhenDisabled(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path outputRoot = directory.resolve("target/generated-sources/jalias");
        MavenProject project = new MavenProject();
        TransformMojo mojo = mojo(aliasRoot, outputRoot, project);
        mojo.setAddSourceRoot(false);

        mojo.execute();

        assertTrue(Files.exists(outputRoot.resolve("com/example/app/App.java")));
        assertTrue(project.getCompileSourceRoots().isEmpty(), project.getCompileSourceRoots().toString());
    }

    @Test
    void canBeConfiguredToDropTheAliasComments(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        writeAliasSource(aliasRoot);
        Path outputRoot = directory.resolve("target/generated-sources/jalias");
        TransformMojo mojo = mojo(aliasRoot, outputRoot, new MavenProject());
        mojo.setEmitAliasComments(false);

        mojo.execute();

        String source = Files.readString(outputRoot.resolve("com/example/app/App.java"));
        assertFalse(source.contains("// import"), source);
        assertTrue(source.contains("java.util.Date util = new java.util.Date(0L);"), source);
    }

    @Test
    void reportsCodeErrorsWhenVerificationIsEnabled(@TempDir Path directory) throws Exception {
        Path aliasRoot = directory.resolve("src/main/jalias");
        Path file = aliasRoot.resolve("com/example/app/Broken.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package com.example.app;

                import java.util.Date as UtilDate;

                public class Broken {
                    public void run() {
                        new UtilDate(0L).missingMethod();
                    }
                }
                """);
        Path outputRoot = directory.resolve("target/generated-sources/jalias");
        TransformMojo mojo = mojo(aliasRoot, outputRoot, new MavenProject());
        mojo.setVerify(true);

        MojoExecutionException failure = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(failure.getMessage().contains("verification failed"), failure.getMessage());
        assertTrue(failure.getMessage().contains("missingMethod"), failure.getMessage());
        assertTrue(Files.exists(outputRoot.resolve("com/example/app/Broken.java")),
                "the generated sources are written even when verification fails");
    }

    @Test
    void exposesTheConfiguredDirectories(@TempDir Path directory) {
        Path aliasRoot = directory.resolve("alias");
        Path outputRoot = directory.resolve("out");
        TransformMojo mojo = mojo(aliasRoot, outputRoot, new MavenProject());

        assertEquals(aliasRoot.toFile(), mojo.sourceDirectory());
        assertEquals(outputRoot.toFile(), mojo.outputDirectory());
    }
}
