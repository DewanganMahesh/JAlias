package io.jalias.parser;

import io.jalias.exceptions.AliasSyntaxException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.ImportAlias;
import io.jalias.model.ImportKind;
import io.jalias.model.SourceUnit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasImportParserTest {

    private final AliasImportParser parser = new AliasImportParser();

    @Test
    void parsesOneAliasedImport() {
        String source = """
                package com.foo;

                import java.util.Date as UtilDate;

                class Example {
                    UtilDate created;
                }
                """;
        CompilationUnitModel model = parser.parse(SourceUnit.of(source));

        assertEquals("com.foo", model.packageName());
        assertTrue(model.hasAliases());
        assertEquals(1, model.aliases().size());
        assertEquals(1, model.imports().size());

        ImportAlias alias = model.aliases().get(0);
        assertEquals("java.util.Date", alias.qualifiedName());
        assertEquals("UtilDate", alias.alias());
        assertEquals(3, alias.line());
        assertEquals(1, alias.column());
        assertEquals("Date", alias.simpleName());

        assertEquals(List.of("UtilDate"), List.copyOf(model.aliasNames()));
        assertEquals(Map.of("UtilDate", "java.util.Date"), model.aliasMappings());
        assertEquals(1, model.aliasLineSpans().size());
        assertEquals(2, model.aliasLineSpans().get(0).startLine());
        assertEquals(2, model.aliasLineSpans().get(0).endLine());
        assertEquals("UtilDate", model.alias("UtilDate").orElseThrow().alias());
        assertTrue(model.alias("Nope").isEmpty());
        assertEquals(ImportKind.ALIAS, model.imports().get(0).kind());
        assertTrue(model.imports().get(0).isAlias());
    }

    @Test
    void replacesTheAliasedImportWithACommentOfTheSameLength() {
        String source = "import java.util.Date as UtilDate;\nclass Example {\n    UtilDate created;\n}\n";
        CompilationUnitModel model = parser.parse(SourceUnit.of(source));

        String expected = "//port java.util.Date as UtilDate;\nclass Example {\n    UtilDate created;\n}\n";
        assertEquals(expected, model.normalizedSource());
        assertEquals(1, model.sourceMap().editCount());
        assertEquals(0, model.sourceMap().toOriginalOffset(0));
        assertEquals(27, model.sourceMap().toOriginalOffset(27));
        assertEquals(5, model.normalizedLineIndex().lineCount());
        assertEquals(5, model.lineIndex().lineCount());
    }

    @Test
    void parsesAliasesMixedWithPlainImports() {
        String source = """
                package com.foo;

                import java.util.List;
                import java.util.*;
                import static java.lang.Math.max;
                import static java.lang.Math.*;
                import com.example.Bar as ExampleBar

                ;
                import com.example.Bar as Bar2;

                class Example {
                }
                """;
        CompilationUnitModel model = parser.parse(SourceUnit.of(source));

        assertEquals(6, model.imports().size());
        assertEquals(2, model.aliases().size());
        assertEquals(4, model.plainImports().size());
        assertEquals("ExampleBar", model.aliases().get(0).alias());
        assertEquals(7, model.aliases().get(0).line());
        assertEquals(10, model.aliases().get(1).line());

        assertEquals(ImportKind.SINGLE, model.imports().get(0).kind());
        assertEquals("List", model.imports().get(0).name());
        assertEquals(ImportKind.WILDCARD, model.imports().get(1).kind());
        assertEquals(ImportKind.STATIC, model.imports().get(2).kind());
        assertEquals("max", model.imports().get(2).name());
        assertEquals("java.lang.Math", model.imports().get(2).qualifiedName());
        assertEquals(ImportKind.STATIC_WILDCARD, model.imports().get(3).kind());
        assertTrue(model.imports().get(1).isWildcard());
        assertTrue(model.imports().get(2).isStatic());
        assertTrue(model.imports().get(0).importedSimpleName().isPresent());
        assertTrue(model.imports().get(1).importedSimpleName().isEmpty());

        assertEquals(model.lineIndex().lineCount(), model.normalizedLineIndex().lineCount());
    }

    @Test
    void ignoresAsWhenItIsJustPartOfAPlainImport() {
        CompilationUnitModel model = parser.parse(SourceUnit.of("""
                import foo.as;
                import as.Foo;
                class Example {
                }
                """));

        assertFalse(model.hasAliases());
        assertEquals(2, model.imports().size());
        assertEquals(ImportKind.SINGLE, model.imports().get(0).kind());
        assertEquals("foo.as", model.imports().get(0).qualifiedName());
        assertEquals("as", model.imports().get(0).name());
        assertEquals("as.Foo", model.imports().get(1).qualifiedName());
    }

    @Test
    void ignoresAliasSyntaxInsideCommentsAndLiterals() {
        CompilationUnitModel model = parser.parse(SourceUnit.of("""
                package com.foo;

                /* import a.B as C; */

                class Example {
                    String text = "import a.B as C;";
                }
                """));

        assertFalse(model.hasAliases());
        assertEquals(0, model.imports().size());
    }

    @Test
    void handlesCommentsBetweenTokens() {
        CompilationUnitModel model = parser.parse(SourceUnit.of("""
                import /* one */ com.foo.Bar /* two */ as /* three */ FooBar;

                class Example {
                }
                """));

        assertEquals(1, model.aliases().size());
        assertEquals("com.foo.Bar", model.aliases().get(0).qualifiedName());
        assertEquals("FooBar", model.aliases().get(0).alias());
    }

    @Test
    void handlesMultiLineAliasedImports() {
        String source = """
                import com.foo.Bar
                        as FooBar;
                class Example {
                    FooBar bar;
                }
                """;
        CompilationUnitModel model = parser.parse(SourceUnit.of(source));

        assertEquals(1, model.aliases().size());
        assertEquals(1, model.aliases().get(0).line());
        assertEquals(0, model.aliasLineSpans().get(0).startLine());
        assertEquals(1, model.aliasLineSpans().get(0).endLine());
        assertEquals(model.lineIndex().lineCount(), model.normalizedLineIndex().lineCount());
        assertTrue(model.normalizedSource().startsWith("//port com.foo.Bar"));
        assertTrue(model.normalizedSource().contains("class Example {"));
    }

    @Test
    void keepsLineNumbersWithWindowsLineEndings() {
        String source = "package com.foo;\r\n\r\nimport java.util.Date as UtilDate;\r\n\r\nclass Example {\r\n}\r\n";
        CompilationUnitModel model = parser.parse(SourceUnit.of(source));

        assertEquals(1, model.aliases().size());
        assertEquals(3, model.aliases().get(0).line());
        assertEquals(7, model.normalizedLineIndex().lineCount());
        assertTrue(model.normalizedSource().contains("\r\n"));
        assertEquals("//port java.util.Date as UtilDate;", model.normalizedSource().split("\r\n")[2]);
    }

    @Test
    void reportsAliasSyntaxAfterTheHeader() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class, () -> parser.parse(SourceUnit.of("""
                package com.foo;

                class Example {
                    import com.foo.Bar as FooBar;
                }
                """)));
        assertTrue(failure.getMessage().contains("must be declared in the import section"), failure.getMessage());
        assertTrue(failure.getMessage().contains("line 4"), failure.getMessage());
    }

    @Test
    void reportsAMissingAliasName() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> parser.parse(SourceUnit.of("import com.foo.Bar as;\nclass Example {}\n")));
        assertTrue(failure.getMessage().contains("Missing alias name after 'as'"), failure.getMessage());
    }

    @Test
    void reportsAMissingSemicolon() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> parser.parse(SourceUnit.of("import com.foo.Bar as FooBar\nclass Example {}\n")));
        assertTrue(failure.getMessage().contains("Unterminated import statement"), failure.getMessage());
        assertTrue(failure.getMessage().contains("line 1"), failure.getMessage());
    }

    @Test
    void reportsTokensAfterTheAlias() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> parser.parse(SourceUnit.of("import com.foo.Bar as FooBar Baz;\nclass Example {}\n")));
        assertTrue(failure.getMessage().contains("Unexpected token 'Baz'"), failure.getMessage());
    }

    @Test
    void reportsStaticAliasImports() {
        InvalidImportException failure = assertThrows(InvalidImportException.class,
                () -> parser.parse(SourceUnit.of("import static com.foo.Bar.CONST as C;\nclass Example {}\n")));
        assertTrue(failure.getMessage().contains("Static alias imports are not supported"), failure.getMessage());
    }

    @Test
    void reportsWildcardAliasImports() {
        InvalidImportException failure = assertThrows(InvalidImportException.class,
                () -> parser.parse(SourceUnit.of("import com.foo.* as Foo;\nclass Example {}\n")));
        assertTrue(failure.getMessage().contains("Wildcard alias imports are not supported"), failure.getMessage());
    }

    @Test
    void reportsInvalidPlainImports() {
        InvalidImportException failure = assertThrows(InvalidImportException.class,
                () -> parser.parse(SourceUnit.of("import as FooBar;\nclass Example {}\n")));
        assertTrue(failure.getMessage().contains("Unexpected token 'FooBar'"), failure.getMessage());
    }

    @Test
    void rejectsKeywordsAsAliasNames() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class,
                () -> parser.parse(SourceUnit.of("import com.foo.Bar as class;\nclass Example {}\n")));
        assertTrue(failure.getMessage().contains("keywords cannot be used as aliases"), failure.getMessage());
    }
}
