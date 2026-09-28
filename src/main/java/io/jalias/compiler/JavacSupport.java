package io.jalias.compiler;

import io.jalias.exceptions.AliasCompilationException;

import javax.tools.Diagnostic;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin helpers around the JDK compiler API. Everything JAlias needs from the compiler goes through
 * this class, which keeps the rest of the code free of {@code javax.tools} noise.
 */
final class JavacSupport {

    private JavacSupport() {
    }

    /**
     * @return the system Java compiler
     * @throws AliasCompilationException if no compiler is available (running on a JRE)
     */
    static JavaCompiler systemCompiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AliasCompilationException("No Java compiler available: JAlias requires a JDK "
                    + "(ToolProvider.getSystemJavaCompiler() returned null)");
        }
        return compiler;
    }

    /**
     * Wraps in-memory source text in a file object whose name matches the type it declares, which is
     * what the compiler requires for public types.
     *
     * @param binaryName binary name of the primary type
     * @param sourceText the Java source
     * @return a compilable file object
     */
    static JavaFileObject sourceFile(String binaryName, String sourceText) {
        String path = "/" + binaryName.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension;
        return new SimpleJavaFileObject(URI.create("string://" + path), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceText;
            }
        };
    }

    /**
     * Formats a diagnostic the way a developer expects to read it.
     *
     * @param diagnostic compiler diagnostic
     * @return {@code file:line:column: kind: message}
     */
    static String format(Diagnostic<? extends JavaFileObject> diagnostic) {
        StringBuilder text = new StringBuilder();
        JavaFileObject source = diagnostic.getSource();
        if (source != null) {
            text.append(displayName(source));
            if (diagnostic.getLineNumber() != Diagnostic.NOPOS) {
                text.append(':').append(diagnostic.getLineNumber());
                if (diagnostic.getColumnNumber() != Diagnostic.NOPOS) {
                    text.append(':').append(diagnostic.getColumnNumber());
                }
            }
            text.append(": ");
        }
        text.append(diagnostic.getKind()).append(": ").append(diagnostic.getMessage(null));
        return text.toString();
    }

    /**
     * @param file a file object created by this class
     * @return a human readable name without the synthetic scheme
     */
    static String displayName(JavaFileObject file) {
        String name = file.getName();
        return name.startsWith("string://") ? name.substring("string://".length()) : name;
    }

    /**
     * Builds the compiler options JAlias always uses, plus the requested ones.
     *
     * @param classpath       classpath entries
     * @param outputDirectory output directory, or {@code null} for in-memory compilation
     * @param extraOptions    additional compiler options
     * @return the option list handed to the compiler
     */
    static List<String> options(List<String> classpath, Path outputDirectory, List<String> extraOptions) {
        List<String> options = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8", "-g"));
        if (!classpath.isEmpty()) {
            options.add("-classpath");
            options.add(String.join(File.pathSeparator, classpath));
        }
        if (outputDirectory != null) {
            options.add("-d");
            options.add(outputDirectory.toString());
        }
        options.addAll(extraOptions);
        return options;
    }

    /**
     * File manager that keeps compiled classes in memory so that they can be loaded and executed
     * without touching the file system.
     */
    static final class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Map<String, ByteArrayOutput> outputs = new LinkedHashMap<>();

        InMemoryFileManager(JavaCompiler compiler, List<String> classpath) throws IOException {
            super(compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8));
            if (!classpath.isEmpty()) {
                List<File> entries = new ArrayList<>();
                for (String entry : classpath) {
                    entries.add(new File(entry));
                }
                fileManager.setLocation(StandardLocation.CLASS_PATH, entries);
            }
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className,
                                                  JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
            if (kind != JavaFileObject.Kind.CLASS) {
                return new ByteArrayOutput(className, kind);
            }
            ByteArrayOutput output = new ByteArrayOutput(className, kind);
            outputs.put(className, output);
            return output;
        }

        /**
         * @return binary name to class file bytes
         */
        Map<String, byte[]> classes() {
            Map<String, byte[]> classes = new LinkedHashMap<>();
            outputs.forEach((name, output) -> classes.put(name, output.bytes()));
            return classes;
        }

        /**
         * Writes all captured classes to disk.
         *
         * @param directory target directory
         * @throws IOException if a file cannot be written
         */
        void writeTo(Path directory) throws IOException {
            for (Map.Entry<String, ByteArrayOutput> entry : outputs.entrySet()) {
                Path target = directory.resolve(entry.getKey().replace('.', '/')
                        + JavaFileObject.Kind.CLASS.extension);
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue().bytes());
            }
        }
    }

    /**
     * A class file (or other compiler output) held in a byte array.
     */
    static class ByteArrayOutput extends SimpleJavaFileObject {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        ByteArrayOutput(String className, JavaFileObject.Kind kind) {
            super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return buffer;
        }

        byte[] bytes() {
            return buffer.toByteArray();
        }
    }
}
