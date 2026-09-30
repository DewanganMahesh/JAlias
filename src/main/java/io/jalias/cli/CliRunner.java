package io.jalias.cli;

import io.jalias.JAlias;
import io.jalias.compiler.AliasCompilationRequest;
import io.jalias.compiler.AliasCompiler;
import io.jalias.compiler.CompilationResult;
import io.jalias.compiler.SourceTreeTransformer;
import io.jalias.compiler.TransformResult;
import io.jalias.exceptions.JAliasException;
import io.jalias.model.AliasOptions;
import io.jalias.model.SourceUnit;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Command line front end for JAlias: rewrite alias syntax, compile it, or compile and run it.
 *
 * <p>{@link #run(String[], PrintStream, PrintStream)} is separated from {@link JAliasCli} so that it
 * can be tested without starting a new JVM.
 */
public final class CliRunner {

    private static final String USAGE = """
            JAlias - import aliasing for Java

            Usage:
              jalias transform [--no-comments] [-o <file|dir>] <source.java ...>
                  Print or write the generated Java source.

              jalias transform --source-dir <dir> [-o <output-dir>] [--no-comments]
                  Transform a whole source tree into a generated sources directory
                  (default: target/generated-sources/jalias). This is the mode a build uses.

              jalias compile [-d <dir>] [-cp <classpath>] <source.java|dir ...>
                  Compile sources that use alias syntax. Without -d the classes stay in memory.

              jalias run [-cp <classpath>] [-main <class>] <source.java ...> [-- <program args>]
                  Compile the sources and run the resulting program.

              jalias version | help

            Example:
              jalias run examples/date-alias/DateAliasExample.java
            """;

    /**
     * Executes one CLI invocation.
     *
     * @param args    command line arguments
     * @param out     stream for normal output
     * @param err     stream for errors
     * @return the process exit code: 0 on success, 1 for failures, 2 for usage problems
     */
    public int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            out.print(USAGE);
            return 0;
        }
        String command = args[0];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        Parsed parsed = parse(rest, err);
        if (parsed == null) {
            return 2;
        }
        try {
            return switch (command) {
                case "transform" -> transform(parsed, out, err);
                case "compile" -> compileCommand(parsed, out, err);
                case "run" -> runProgram(parsed, out, err);
                case "version", "-v", "--version" -> {
                    out.println("JAlias " + JAlias.version());
                    yield 0;
                }
                case "help", "-h", "--help" -> {
                    out.print(USAGE);
                    yield 0;
                }
                default -> {
                    err.println("Unknown command '" + command + "'");
                    err.print(USAGE);
                    yield 2;
                }
            };
        } catch (JAliasException e) {
            err.println(e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("I/O failure: " + e.getMessage());
            return 1;
        }
    }

    private int transform(Parsed parsed, PrintStream out, PrintStream err) throws IOException {
        if (parsed.sourceDirectory != null) {
            return transformTree(parsed, out, err);
        }
        List<Path> files = collectSources(parsed.sources, err);
        if (files.isEmpty()) {
            err.println("transform needs at least one .java file");
            return 2;
        }
        AliasCompiler compiler = new AliasCompiler(parsed.options());
        for (Path file : files) {
            SourceUnit unit = SourceUnit.fromPath(file);
            TransformResult result = compiler.transform(unit);
            if (parsed.output != null && files.size() == 1) {
                Path target = parsed.output;
                if (Files.isDirectory(target)) {
                    target = target.resolve(unit.fileName());
                }
                Files.createDirectories(target.toAbsolutePath().getParent());
                Files.writeString(target, result.transformedSource(), StandardCharsets.UTF_8);
                out.println("wrote " + target + " (" + result.replacements() + " reference(s) rewritten)");
            } else {
                if (files.size() > 1) {
                    out.println("// ---- " + file + " ----");
                }
                out.print(result.transformedSource());
                if (!result.transformedSource().endsWith("\n")) {
                    out.println();
                }
                out.println("// " + result.summary());
            }
        }
        return 0;
    }

    /**
     * Transforms a whole source tree, the mode a build integration uses.
     */
    private int transformTree(Parsed parsed, PrintStream out, PrintStream err) throws IOException {
        Path outputRoot = parsed.output != null
                ? parsed.output
                : Path.of("target", "generated-sources", "jalias");
        SourceTreeTransformer.Result result =
                new SourceTreeTransformer(parsed.options()).transform(parsed.sourceDirectory, outputRoot);

        out.println(result.summary());
        for (SourceTreeTransformer.TransformedFile file : result.withAliases()) {
            out.println("  " + file.source().getFileName() + " -> " + file.aliases()
                    + " (" + file.replacements() + " reference(s) rewritten)");
        }
        return 0;
    }

    private int compileCommand(Parsed parsed, PrintStream out, PrintStream err) throws IOException {
        List<Path> files = collectSources(parsed.sources, err);
        if (files.isEmpty()) {
            err.println("compile needs at least one .java file or directory");
            return 2;
        }
        CompilationResult result = compile(parsed, files, parsed.output, err);
        if (result == null) {
            return 2;
        }
        result.warnings().forEach(out::println);
        if (!result.success()) {
            result.problems().forEach(err::println);
            return 1;
        }
        if (parsed.output != null) {
            out.println("compiled " + countClassFiles(parsed.output) + " class(es) to " + parsed.output);
        } else {
            out.println("compiled " + result.classes().size()
                    + " class(es) in memory (use -d <dir> to write class files)");
        }
        if (!result.aliasMappings().isEmpty()) {
            out.println("aliases: " + result.aliasMappings());
        }
        return 0;
    }

    private long countClassFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(path -> path.toString().endsWith(".class")).count();
        } catch (IOException e) {
            return 0L;
        }
    }

    private int runProgram(Parsed parsed, PrintStream out, PrintStream err) throws IOException {
        List<Path> files = collectSources(parsed.sources, err);
        if (files.isEmpty()) {
            err.println("run needs at least one .java file");
            return 2;
        }
        String mainClass = parsed.mainClass;
        if (mainClass == null) {
            if (files.size() != 1) {
                err.println("run needs -main <class> when more than one source file is given");
                return 2;
            }
            mainClass = SourceUnit.fromPath(files.get(0)).binaryName();
        }
        Path outputDirectory = Files.createTempDirectory("jalias-run");
        CompilationResult result = compile(parsed, files, outputDirectory, err);
        if (result == null) {
            return 2;
        }
        if (!result.success()) {
            result.problems().forEach(err::println);
            return 1;
        }
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-classpath");
        command.add(outputDirectory + File.pathSeparator + parsed.classpath());
        command.add(mainClass);
        command.addAll(parsed.programArgs);
        out.println("running " + mainClass);
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true);
        Process process = builder.start();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
        }
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("interrupted while running " + mainClass);
            return 1;
        }
    }

    private CompilationResult compile(Parsed parsed, List<Path> files, Path outputDirectory, PrintStream err)
            throws IOException {
        List<SourceUnit> units = new ArrayList<>();
        for (Path file : files) {
            units.add(SourceUnit.fromPath(file));
        }
        AliasCompilationRequest.Builder builder = AliasCompilationRequest.builder().sources(units);
        if (outputDirectory != null) {
            builder.outputDirectory(outputDirectory);
        }
        if (!parsed.classpath().isBlank()) {
            builder.addClasspath(parsed.classpath());
        }
        return new AliasCompiler(parsed.options()).compile(builder.build());
    }

    private List<Path> collectSources(List<Path> inputs, PrintStream err) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                try (Stream<Path> stream = Files.walk(input)) {
                    stream.filter(path -> path.toString().endsWith(".java"))
                            .sorted(Comparator.comparing(Path::toString))
                            .forEach(files::add);
                }
            } else if (Files.exists(input)) {
                files.add(input);
            } else {
                err.println("skipping '" + input + "': file does not exist");
            }
        }
        return files;
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    /**
     * Parsed command line options.
     */
    private static final class Parsed {

        private final List<Path> sources = new ArrayList<>();
        private final List<String> programArgs = new ArrayList<>();
        private Path output;
        private Path sourceDirectory;
        private String classpath = "";
        private String mainClass;
        private boolean emitComments = true;

        AliasOptions options() {
            return AliasOptions.defaults().withEmitAliasComments(emitComments);
        }

        String classpath() {
            return classpath;
        }
    }

    private Parsed parse(String[] args, PrintStream err) {
        Parsed parsed = new Parsed();
        boolean programArguments = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (programArguments) {
                parsed.programArgs.add(arg);
            } else if (arg.equals("--")) {
                programArguments = true;
            } else if (arg.equals("-d") || arg.equals("--output-directory") || arg.equals("-o")
                    || arg.equals("--output")) {
                if (++i >= args.length) {
                    err.println(arg + " needs a value");
                    return null;
                }
                parsed.output = Path.of(args[i]);
            } else if (arg.equals("-cp") || arg.equals("--classpath")) {
                if (++i >= args.length) {
                    err.println("-cp needs a value");
                    return null;
                }
                parsed.classpath = args[i];
            } else if (arg.equals("-s") || arg.equals("--source-dir")) {
                if (++i >= args.length) {
                    err.println(arg + " needs a value");
                    return null;
                }
                parsed.sourceDirectory = Path.of(args[i]);
            } else if (arg.equals("-main") || arg.equals("--main-class")) {
                if (++i >= args.length) {
                    err.println("-main needs a value");
                    return null;
                }
                parsed.mainClass = args[i];
            } else if (arg.equals("--no-comments")) {
                parsed.emitComments = false;
            } else if (arg.startsWith("-")) {
                err.println("Unknown option '" + arg + "'");
                return null;
            } else {
                parsed.sources.add(Path.of(arg));
            }
        }
        return parsed;
    }
}
