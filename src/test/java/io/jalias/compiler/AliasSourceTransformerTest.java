package io.jalias.compiler;

import io.jalias.exceptions.AliasConflictException;
import io.jalias.model.AliasOptions;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.SourceUnit;
import io.jalias.parser.AliasImportParser;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden tests for the source transformation: which parts of a source file are rewritten, which parts
 * are left alone, and what the generated Java looks like.
 */
class AliasSourceTransformerTest {

    private final AliasImportParser parser = new AliasImportParser();

    private String transform(String source) {
        return new AliasSourceTransformer().transform(parser.parse(SourceUnit.of(source))).transformedSource();
    }

    private CompilationUnitModel parse(String source) {
        return parser.parse(SourceUnit.of(source));
    }

    @Test
    void rewritesFieldsParametersAndReturnTypes() {
        String transformed = transform("""
                import com.foo.Bar as FooBar;

                class Example {
                    FooBar field;
                    void set(FooBar value) { this.field = value; }
                    FooBar get() { return field; }
                }
                """);

        assertTrue(transformed.contains("// import com.foo.Bar as FooBar;"), transformed);
        assertTrue(transformed.contains("com.foo.Bar field;"), transformed);
        assertTrue(transformed.contains("void set(com.foo.Bar value)"), transformed);
        assertTrue(transformed.contains("com.foo.Bar get()"), transformed);
        assertFalse(transformed.contains("FooBar field"), transformed);
    }

    @Test
    void rewritesConstructorsStaticMembersNestedTypesAndReferences() {
        String transformed = transform("""
                import com.foo.Bar as FooBar;

                class Example {
                    FooBar one = new FooBar();
                    FooBar two = new FooBar(1);
                    String origin = FooBar.origin();
                    Object constant = FooBar.CONSTANT;
                    FooBar.Nested nested = new FooBar.Nested();
                    Class<?> literal = FooBar.class;
                    java.util.function.Supplier<FooBar> factory = FooBar::new;
                }
                """);

        assertTrue(transformed.contains("com.foo.Bar one = new com.foo.Bar();"), transformed);
        assertTrue(transformed.contains("new com.foo.Bar(1)"), transformed);
        assertTrue(transformed.contains("com.foo.Bar.origin()"), transformed);
        assertTrue(transformed.contains("com.foo.Bar.CONSTANT"), transformed);
        assertTrue(transformed.contains("com.foo.Bar.Nested nested = new com.foo.Bar.Nested();"), transformed);
        assertTrue(transformed.contains("com.foo.Bar.class"), transformed);
        assertTrue(transformed.contains("java.util.function.Supplier<com.foo.Bar> factory = com.foo.Bar::new;"),
                transformed);
    }

    @Test
    void rewritesGenericsArraysCastsAndInstanceof() {
        String transformed = transform("""
                import com.foo.Bar as FooBar;

                class Example {
                    java.util.List<FooBar> list;
                    java.util.Map<FooBar, java.util.List<FooBar>> map;
                    FooBar[] array = new FooBar[3];
                    FooBar[][] matrix = new FooBar[1][1];

                    void check(Object value) {
                        if (value instanceof FooBar) {
                            FooBar cast = (FooBar) value;
                            System.out.println(cast);
                        }
                    }
                }
                """);

        assertTrue(transformed.contains("java.util.List<com.foo.Bar> list;"), transformed);
        assertTrue(transformed.contains("java.util.Map<com.foo.Bar, java.util.List<com.foo.Bar>> map;"), transformed);
        assertTrue(transformed.contains("com.foo.Bar[] array = new com.foo.Bar[3];"), transformed);
        assertTrue(transformed.contains("com.foo.Bar[][] matrix = new com.foo.Bar[1][1];"), transformed);
        assertTrue(transformed.contains("value instanceof com.foo.Bar"), transformed);
        assertTrue(transformed.contains("com.foo.Bar cast = (com.foo.Bar) value;"), transformed);
    }

    @Test
    void rewritesAnnotationsExtendsImplementsAndThrows() {
        String annotation = transform("""
                import java.lang.Override as Alias;

                class Example {
                    @Alias
                    public String toString() { return "x"; }
                }
                """);
        assertTrue(annotation.contains("@java.lang.Override"), annotation);

        String closeable = transform("""
                import java.io.Closeable as Alias;

                class Example implements Alias {
                    public void close() throws java.io.IOException { }
                }
                """);
        assertTrue(closeable.contains("implements java.io.Closeable"), closeable);

        String extended = transform("""
                import java.util.AbstractList as Alias;

                class Example extends Alias<String> {
                    public String get(int index) { return null; }
                    public int size() { return 0; }
                }
                """);
        assertTrue(extended.contains("extends java.util.AbstractList<String>"), extended);
    }

    @Test
    void neverRewritesMemberNames() {
        String transformed = transform("""
                import java.util.Map as JMap;
                import java.util.Map.Entry as Entry;

                class Example {
                    JMap.Entry<String, String> memberName;
                    Entry<String, String> viaAlias;
                    JMap<String, String> plain;
                }
                """);

        assertTrue(transformed.contains("java.util.Map.Entry<String, String> memberName;"), transformed);
        assertTrue(transformed.contains("java.util.Map.Entry<String, String> viaAlias;"), transformed);
        assertTrue(transformed.contains("java.util.Map<String, String> plain;"), transformed);
        assertFalse(transformed.contains("java.util.Map.java.util.Map"), transformed);
    }

    @Test
    void leavesStringsCommentsAndJavadocAlone() {
        String transformed = transform("""
                import com.foo.Bar as FooBar;

                /** Javadoc mentioning FooBar and {@code FooBar}. */
                class Example {
                    // FooBar in a line comment
                    /* FooBar in a block comment */
                    String text = "FooBar is only text here";
                    FooBar field;
                }
                """);

        assertTrue(transformed.contains("/** Javadoc mentioning FooBar and {@code FooBar}. */"), transformed);
        assertTrue(transformed.contains("// FooBar in a line comment"), transformed);
        assertTrue(transformed.contains("/* FooBar in a block comment */"), transformed);
        assertTrue(transformed.contains("\"FooBar is only text here\""), transformed);
        assertTrue(transformed.contains("com.foo.Bar field;"), transformed);
    }

    @Test
    void rewritesPatternsAndRecordComponents() {
        String transformed = transform("""
                import com.foo.Bar as FooBar;

                record Holder(FooBar bar) {
                    String describe(Object value) {
                        return switch (value) {
                            case FooBar bar -> bar.toString();
                            default -> "other";
                        };
                    }
                }
                """);

        assertTrue(transformed.contains("record Holder(com.foo.Bar bar)"), transformed);
        assertTrue(transformed.contains("case com.foo.Bar bar ->"), transformed);
    }

    @Test
    void rewritesSealedHierarchiesAndGenericBounds() {
        String transformed = transform("""
                import com.foo.Bar as FooBar;

                sealed interface Shape permits FooBar { }

                class Box<T extends FooBar> {
                    <U extends FooBar> void accept(java.util.List<? extends FooBar> values) { }
                }
                """);

        assertTrue(transformed.contains("permits com.foo.Bar"), transformed);
        assertTrue(transformed.contains("class Box<T extends com.foo.Bar>"), transformed);
        assertTrue(transformed.contains("<U extends com.foo.Bar>"), transformed);
        assertTrue(transformed.contains("java.util.List<? extends com.foo.Bar>"), transformed);
    }

    @Test
    void keepsLineNumbersAndBlanksAliasLinesWhenCommentsAreDisabled() {
        String source = "import com.foo.Bar as FooBar;\n\nclass Example {\n    FooBar field;\n}\n";
        TransformResult result = new AliasSourceTransformer(
                AliasOptions.defaults().withEmitAliasComments(false)).transform(parse(source));

        String transformed = result.transformedSource();
        assertFalse(transformed.contains("as FooBar"), transformed);
        assertEquals(source.lines().count(), transformed.lines().count());
        assertTrue(transformed.contains("com.foo.Bar field;"), transformed);
        assertEquals(1, result.replacements());
        assertEquals(java.util.Map.of("FooBar", "com.foo.Bar"), result.aliasMappings());
        assertTrue(result.hasChanges());
        assertTrue(result.summary().contains("1 reference(s) rewritten"), result.summary());
    }

    @Test
    void reportsAliasesUsedAsMethodNames() {
        AliasConflictException failure = assertThrows(AliasConflictException.class, () -> transform("""
                import com.foo.Bar as FooBar;

                class Example extends Base {
                    void run() {
                        FooBar();
                    }
                }
                """));
        assertTrue(failure.getMessage().contains("Alias 'FooBar' is used as a method name"), failure.getMessage());
        assertTrue(failure.getMessage().contains("line 5"), failure.getMessage());
    }

    @Test
    void reportsAliasesUsedAsCaseLabels() {
        AliasConflictException failure = assertThrows(AliasConflictException.class, () -> transform("""
                import com.foo.Bar as FooBar;

                class Example {
                    void run(int value) {
                        switch (value) {
                            case FooBar: break;
                            default: break;
                        }
                    }
                }
                """));
        assertTrue(failure.getMessage().contains("Alias 'FooBar' is used as a case label"), failure.getMessage());
    }

    @Test
    void reportsSourcesWithoutAliasesAsUnchanged() {
        String source = "class Example {\n    int value;\n}\n";
        TransformResult result = new AliasSourceTransformer().transform(parse(source));
        assertEquals(source, result.transformedSource());
        assertFalse(result.hasChanges());
        assertEquals(0, result.replacements());
    }

    @Test
    void producesCompilableJava() {
        String transformed = transform("""
                import java.util.Date as UtilDate;
                import java.sql.Date as SqlDate;

                class Example {
                    UtilDate created = new UtilDate(0L);
                    SqlDate stored = new SqlDate(0L);
                }
                """);

        CompilationResult result = TestHarness.compile(SourceUnit.of("Example", transformed));
        assertTrue(result.success(), result.problems().toString());
    }
}
