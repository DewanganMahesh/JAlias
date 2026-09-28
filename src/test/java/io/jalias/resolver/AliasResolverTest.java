package io.jalias.resolver;

import io.jalias.exceptions.AliasNotFoundException;
import io.jalias.model.ImportAlias;
import io.jalias.model.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasResolverTest {

    private final ImportAlias fooBar = ImportAlias.of("com.foo.Bar", "FooBar");
    private final ImportAlias exampleBar = ImportAlias.of("com.example.Bar", "ExampleBar");

    @Test
    void exposesTheDeclaredAliases() {
        AliasResolver resolver = AliasResolver.of(List.of(fooBar, exampleBar));
        assertEquals(List.of("ExampleBar", "FooBar"), resolver.aliases());
        assertEquals(2, resolver.declaredAliases().size());
        assertTrue(resolver.isAlias("FooBar"));
        assertFalse(resolver.isAlias("Bar"));
        assertFalse(resolver.isAlias(null));
    }

    @Test
    void hasAnEmptyResolver() {
        AliasResolver resolver = AliasResolver.empty();
        assertTrue(resolver.declaredAliases().isEmpty());
        assertFalse(resolver.isAlias("FooBar"));
        AliasNotFoundException failure = assertThrows(AliasNotFoundException.class, () -> resolver.require("FooBar"));
        assertTrue(failure.getMessage().contains("Unknown alias 'FooBar'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("[]"), failure.getMessage());
    }

    @Test
    void buildsFromVarargs() {
        AliasResolver resolver = AliasResolver.of(fooBar);
        assertEquals("com.foo.Bar", resolver.require("FooBar").qualifiedName());
    }

    @Test
    void resolvesTypeNames() {
        AliasResolver resolver = AliasResolver.of(List.of(fooBar));
        assertEquals("com.foo.Bar", resolver.resolveTypeName("FooBar"));
        assertEquals("com.example.Bar", resolver.resolveTypeName("com.example.Bar"));
        AliasNotFoundException failure =
                assertThrows(AliasNotFoundException.class, () -> resolver.resolveTypeName("ExampleBar"));
        assertTrue(failure.getMessage().contains("Unknown alias 'ExampleBar'"), failure.getMessage());
    }

    @Test
    void snapshotsAreIndependentOfLaterRegistrations() {
        AliasRegistry registry = new AliasRegistry().register(fooBar);
        AliasResolver resolver = new DefaultAliasResolver(registry.declaredAliases());
        registry.register(exampleBar);

        assertEquals(1, resolver.declaredAliases().size());
        assertEquals("com.foo.Bar", new TypeReference("FooBar", 1, 1).resolve(resolver));
        assertFalse(new TypeReference("ExampleBar", 1, 1).isAlias(resolver));
    }

    @Test
    void describesThemselvesInMessages() {
        assertEquals("DefaultAliasResolver[]", DefaultAliasResolver.empty().toString());
        assertEquals("DefaultAliasResolver[FooBar]", new DefaultAliasResolver(List.of(fooBar)).toString());
    }
}
