package io.jalias.compiler;

import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasClassLoaderTest {

    @Test
    void definesCompiledClasses() throws ClassNotFoundException {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Example", """
                import java.util.Date as UtilDate;

                class Example {
                    UtilDate created = new UtilDate(0L);
                    String describe() { return created.getClass().getName(); }
                }
                """));

        ClassLoader loader = result.classLoader();
        assertTrue(loader instanceof AliasClassLoader);
        AliasClassLoader typed = (AliasClassLoader) loader;
        assertEquals(java.util.Set.of("Example"), typed.definedClassNames());
        assertEquals("Example", typed.definedClassNames().iterator().next());

        Class<?> type = loader.loadClass("Example");
        assertSame(type, loader.loadClass("Example"));
        assertEquals("Example", type.getName());
        Object instance = TestHarness.newInstance(type);
        assertEquals("java.util.Date", TestHarness.invoke(instance, "describe"));
    }

    @Test
    void delegatesToTheParentLoader() throws ClassNotFoundException {
        AliasClassLoader loader = new AliasClassLoader(Map.of(), getClass().getClassLoader());
        assertSame(java.util.Date.class, loader.loadClass("java.util.Date"));
        assertTrue(loader.definedClassNames().isEmpty());
    }

    @Test
    void reportsMissingClasses() {
        AliasClassLoader loader = new AliasClassLoader(Map.of("Example", new byte[0]), getClass().getClassLoader());
        ClassNotFoundException failure =
                assertThrows(ClassNotFoundException.class, () -> loader.loadClass("Missing"));
        assertTrue(failure.getMessage().contains("Missing"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Example"), failure.getMessage());
    }

    @Test
    void exposesTheClassBytes() {
        CompilationResult result = TestHarness.compile(TestHarness.unit("Example", "class Example {}\n"));
        AliasClassLoader loader = new AliasClassLoader(result.classes(), getClass().getClassLoader());
        assertEquals(result.classes().keySet(), loader.classBytes().keySet());
        assertTrue(loader.classBytes().get("Example").length > 0);
    }
}
