package io.jalias.compiler;

import io.jalias.exceptions.AliasConflictException;
import io.jalias.model.AliasOptions;
import io.jalias.model.SourceUnit;
import io.jalias.parser.AliasImportParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aliases are name replacements, so a file that also declares a variable, method, label, nested type
 * or type parameter with an alias name is rejected instead of guessed at.
 */
class AliasDeclarationConflictValidatorTest {

    private final AliasImportParser parser = new AliasImportParser();

    private void transform(String source) {
        new AliasSourceTransformer().transform(parser.parse(SourceUnit.of(source)));
    }

    private static String withAlias(String body) {
        return "import com.foo.Bar as FooBar;\n\nclass Example {\n" + body + "\n}\n";
    }

    @Test
    void rejectsFieldsWithTheAliasName() {
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    FooBar FooBar;")));
        assertTrue(failure.getMessage().contains("Alias 'FooBar' conflicts with the variable 'FooBar'"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("line 4"), failure.getMessage());
    }

    @Test
    void rejectsLocalsParametersAndLambdaParameters() {
        assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    void run() { int FooBar = 1; }")));
        assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    void run(int FooBar) { }")));
        assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    java.util.function.IntUnaryOperator f = (int FooBar) -> FooBar;")));
    }

    @Test
    void rejectsMethodsWithTheAliasName() {
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    void FooBar() { }")));
        assertTrue(failure.getMessage().contains("conflicts with the method 'FooBar'"), failure.getMessage());
    }

    @Test
    void rejectsNestedTypesEnumConstantsAndTypeParameters() {
        AliasConflictException nested = assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    static class FooBar { }")));
        assertTrue(nested.getMessage().contains("conflicts with the type 'FooBar'"), nested.getMessage());

        assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    enum Kind { FooBar }")));
        assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    static class Box<FooBar> { }")));
    }

    @Test
    void rejectsLabelsAndPatternVariables() {
        AliasConflictException label = assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    void run() { FooBar: while (true) { break FooBar; } }")));
        assertTrue(label.getMessage().contains("conflicts with the label 'FooBar'"), label.getMessage());

        assertThrows(AliasConflictException.class,
                () -> transform(withAlias("    void run(Object value) { if (value instanceof String FooBar) { } }")));
    }

    @Test
    void canBeDisabledForSourcesThatAreKnownToBeUnambiguous() {
        String source = withAlias("    FooBar field;\n    void run() { int FooBar = 1; }");
        TransformResult result = assertDoesNotThrow(() -> new AliasSourceTransformer(
                AliasOptions.defaults().withAllowDeclarationNameConflicts(true)).transform(parser.parse(
                        SourceUnit.of(source))));
        assertTrue(result.transformedSource().contains("com.foo.Bar field;"), result.transformedSource());
    }

    @Test
    void acceptsSourcesWithoutConflicts() {
        assertDoesNotThrow(() -> transform("""
                import com.foo.Bar as FooBar;

                class Example {
                    FooBar field;
                    void run(FooBar value) {
                        FooBar local = value;
                        label: while (true) { break label; }
                    }
                }
                """));
    }
}
