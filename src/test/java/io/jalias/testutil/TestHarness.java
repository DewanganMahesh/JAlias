package io.jalias.testutil;

import io.jalias.compiler.AliasCompilationRequest;
import io.jalias.compiler.AliasCompiler;
import io.jalias.compiler.CompilationResult;
import io.jalias.model.SourceUnit;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Small helpers shared by the JAlias tests: compiling alias sources, calling the compiled code
 * reflectively (the generated types are often package private, just as the source they came from) and
 * locating project files.
 */
public final class TestHarness {

    private TestHarness() {
    }

    /**
     * @param source Java source
     * @return a source unit with a binary name derived from the source
     */
    public static SourceUnit unit(String source) {
        return SourceUnit.of(source);
    }

    /**
     * @param binaryName binary name of the primary type
     * @param source     Java source
     * @return the source unit
     */
    public static SourceUnit unit(String binaryName, String source) {
        return SourceUnit.of(binaryName, source);
    }

    /**
     * Compiles the given sources in memory.
     *
     * @param sources Java sources
     * @return the compilation result
     */
    public static CompilationResult compile(String... sources) {
        List<SourceUnit> units = new ArrayList<>();
        for (String source : sources) {
            units.add(SourceUnit.of(source));
        }
        return compile(units);
    }

    /**
     * Compiles the given units in memory.
     *
     * @param units sources
     * @return the compilation result
     */
    public static CompilationResult compile(List<SourceUnit> units) {
        return new AliasCompiler().compile(AliasCompilationRequest.builder().sources(units).build());
    }

    /**
     * Compiles a single unit in memory.
     *
     * @param unit source
     * @return the compilation result
     */
    public static CompilationResult compile(SourceUnit unit) {
        return compile(List.of(unit));
    }

    /**
     * Compiles the given units with a classpath, optionally writing class files to disk.
     *
     * @param units           sources
     * @param outputDirectory output directory, or {@code null} for in-memory compilation
     * @param classpath       classpath entries
     * @return the compilation result
     */
    public static CompilationResult compile(List<SourceUnit> units, Path outputDirectory, List<Path> classpath) {
        AliasCompilationRequest.Builder builder = AliasCompilationRequest.builder().sources(units);
        if (outputDirectory != null) {
            builder.outputDirectory(outputDirectory);
        }
        if (classpath != null) {
            builder.addClasspath(classpath.toArray(Path[]::new));
        }
        return new AliasCompiler().compile(builder.build());
    }

    /**
     * Instantiates a compiled class through its no-argument constructor.
     *
     * @param type class to instantiate
     * @return the instance
     */
    public static Object newInstance(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot instantiate " + type.getName(), e);
        }
    }

    /**
     * Calls an instance method reflectively, ignoring the access modifier of the compiled source.
     *
     * @param target instance
     * @param method method name
     * @param args   arguments
     * @return the return value
     */
    public static Object invoke(Object target, String method, Object... args) {
        try {
            Method declared = findMethod(target.getClass(), method, args.length);
            declared.setAccessible(true);
            return declared.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot call " + method + " on " + target.getClass().getName(), e);
        }
    }

    /**
     * Calls a static method reflectively.
     *
     * @param type   class owning the method
     * @param method method name
     * @param args   arguments
     * @return the return value
     */
    public static Object call(Class<?> type, String method, Object... args) {
        try {
            Method declared = findMethod(type, method, args.length);
            declared.setAccessible(true);
            return declared.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot call static " + method + " on " + type.getName(), e);
        }
    }

    /**
     * Instantiates a compiled class through a specific constructor.
     *
     * @param result         compilation result holding the class
     * @param binaryName     binary name of the class
     * @param parameterTypes parameter types of the constructor
     * @param args           constructor arguments
     * @return the instance
     */
    public static Object construct(CompilationResult result, String binaryName, Class<?>[] parameterTypes,
                                   Object... args) {
        try {
            Class<?> type = result.loadClass(binaryName);
            java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot construct " + binaryName, e);
        }
    }

    /**
     * Reads an instance field reflectively.
     *
     * @param target instance
     * @param name   field name
     * @return the value
     */
    public static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot read field " + name + " of " + target.getClass().getName(), e);
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new AssertionError("No method " + name + " with " + parameterCount + " parameter(s) on "
                + type.getName());
    }

    /**
     * @return the project root, located by looking for {@code pom.xml}
     */
    public static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the project root: no pom.xml found above "
                + Path.of("").toAbsolutePath());
    }

    /**
     * @param directory directory to scan
     * @return every {@code .java} file below the directory, sorted
     */
    public static List<Path> javaFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot walk " + directory, e);
        }
    }

    /**
     * Reads source files as units.
     *
     * @param files files to read
     * @return the parsed units
     */
    public static List<SourceUnit> units(List<Path> files) {
        List<SourceUnit> units = new ArrayList<>();
        for (Path file : files) {
            try {
                units.add(SourceUnit.fromPath(file));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + file, e);
            }
        }
        return units;
    }
}
