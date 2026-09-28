package io.jalias.model;

import io.jalias.exceptions.AliasSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportAliasTest {

    @Test
    void describesItselfLikeTheSourceItCameFrom() {
        ImportAlias alias = new ImportAlias("com.foo.Bar", "FooBar", "Example.java", 3, 1);
        assertEquals("Bar", alias.simpleName());
        assertEquals("import com.foo.Bar as FooBar;", alias.declaration());
        assertEquals("import com.foo.Bar as FooBar;", alias.toString());
        assertEquals("Example.java:3", alias.location());
        assertEquals(3, alias.line());
        assertEquals(1, alias.column());
    }

    @Test
    void supportsNestedClassTargets() {
        ImportAlias alias = ImportAlias.of("java.util.AbstractMap.SimpleEntry", "JEntry");
        assertEquals("SimpleEntry", alias.simpleName());
        assertEquals("<inline>", alias.location());
    }

    @Test
    void rejectsAnEmptyTarget() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> ImportAlias.of("", "FooBar"));
        assertTrue(failure.getMessage().contains("must not be empty"), failure.getMessage());
    }

    @Test
    void rejectsAnInvalidTarget() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> ImportAlias.of("com.foo.Bar; import x", "FooBar"));
        assertTrue(failure.getMessage().contains("Invalid import target"), failure.getMessage());
        assertTrue(failure.getMessage().contains("qualified class name"), failure.getMessage());
    }

    @Test
    void rejectsKeywordsAsAliases() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> ImportAlias.of("com.foo.Bar", "class"));
        assertTrue(failure.getMessage().contains("keywords cannot be used as aliases"), failure.getMessage());
    }

    @Test
    void rejectsInvalidAliases() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> ImportAlias.of("com.foo.Bar", "Foo Bar"));
        assertTrue(failure.getMessage().contains("not a valid Java identifier"), failure.getMessage());
        assertThrows(AliasSyntaxException.class, () -> ImportAlias.of("com.foo.Bar", ""));
    }
}
