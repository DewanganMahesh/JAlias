package io.jalias.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceUnitTest {

    @Test
    void infersBinaryNameFromPackageAndType() {
        assertEquals("Example", SourceUnit.inferBinaryName("class Example {}"));
        assertEquals("com.foo.Example", SourceUnit.inferBinaryName("package com.foo;\n\npublic class Example {}"));
        assertEquals("com.foo.Outer", SourceUnit.inferBinaryName("package com.foo;\npublic record Outer(int a) {}"));
        assertEquals("com.foo.Kind", SourceUnit.inferBinaryName("package com.foo;\npublic enum Kind { A }"));
        assertEquals("com.foo.Api", SourceUnit.inferBinaryName("package com.foo;\n@interface Api {}"));
        assertEquals("com.foo.Example", SourceUnit.inferBinaryName("package com.foo;\n@Deprecated\npublic final class Example {}"));
        assertEquals("UnnamedUnit", SourceUnit.inferBinaryName("// nothing here\n"));
    }

    @Test
    void readsSourcesFromDisk(@TempDir Path directory) throws IOException {
        Path file = Files.createDirectories(directory.resolve("com/foo")).resolve("Example.java");
        Files.writeString(file, """
                package com.foo;

                import java.util.Date as UtilDate;

                public class Example {
                    UtilDate created;
                }
                """);

        SourceUnit unit = SourceUnit.fromPath(file);
        assertEquals("com.foo.Example", unit.binaryName());
        assertEquals("com.foo", unit.packageName());
        assertEquals("Example", unit.simpleName());
        assertEquals("Example.java", unit.fileName());
        assertEquals(file.toAbsolutePath().normalize(), unit.origin());
        assertEquals(file.toAbsolutePath().normalize().toString(), unit.displayName());
    }

    @Test
    void describesInMemoryUnits() {
        SourceUnit unit = SourceUnit.of("com.foo.Example", "class Example {}");
        assertNull(unit.origin());
        assertEquals("Example.java", unit.displayName());
        assertEquals("com.foo", unit.packageName());
    }

    @Test
    void validatesBinaryNames() {
        assertThrows(IllegalArgumentException.class, () -> SourceUnit.of("", "class A {}"));
        assertThrows(IllegalArgumentException.class, () -> SourceUnit.of("com..A", "class A {}"));
    }
}
