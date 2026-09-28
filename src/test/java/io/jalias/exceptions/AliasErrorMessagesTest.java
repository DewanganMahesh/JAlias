package io.jalias.exceptions;

import io.jalias.JAlias;
import io.jalias.compiler.AliasCompiler;
import io.jalias.compiler.AliasSourceTransformer;
import io.jalias.model.ImportAlias;
import io.jalias.model.SourceUnit;
import io.jalias.model.TypeReference;
import io.jalias.resolver.AliasRegistry;
import io.jalias.testutil.TestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per error category required by the problem statement, asserting both the exception type and
 * the readability of the message.
 */
class AliasErrorMessagesTest {

    @Test
    void duplicateAlias() {
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> JAlias.registry(ImportAlias.of("com.foo.Bar", "FooBar"),
                        ImportAlias.of("com.example.Bar", "FooBar")));
        assertEquals("Alias 'FooBar' is already mapped to com.foo.Bar", failure.getMessage());
        assertTrue(failure instanceof JAliasException);
    }

    @Test
    void duplicateAliasedImportOfTheSameClass() {
        AliasRegistry registry = JAlias.registry(ImportAlias.of("com.foo.Bar", "FooBar"));
        AliasConflictException failure = assertThrows(AliasConflictException.class,
                () -> registry.register(ImportAlias.of("com.foo.Bar", "FooBar")));
        assertTrue(failure.getMessage().startsWith("Duplicate aliased import"), failure.getMessage());
    }

    @Test
    void aliasConflictsWithATypeDeclaredInTheCompilation() {
        AliasConflictException failure = assertThrows(AliasConflictException.class, () -> TestHarness.compile(
                TestHarness.unit("Example", """
                        import java.util.Date as FooBar;

                        class Example {
                        }

                        class FooBar {
                        }
                        """)));
        assertTrue(failure.getMessage().contains("Alias 'FooBar' conflicts with"), failure.getMessage());
        assertTrue(failure.getMessage().contains("FooBar"), failure.getMessage());
    }

    @Test
    void aliasConflictsWithAJavaLangType() {
        AliasConflictException failure = assertThrows(AliasConflictException.class, () -> TestHarness.compile(
                TestHarness.unit("Example", "import java.util.Date as String;\n\nclass Example {\n}\n")));
        assertTrue(failure.getMessage().contains("Alias 'String' conflicts with existing type 'java.lang.String'"),
                failure.getMessage());
    }

    @Test
    void aliasConflictsWithAnExistingImport() {
        AliasConflictException failure = assertThrows(AliasConflictException.class, () -> TestHarness.compile(
                TestHarness.unit("Example", """
                        import java.util.Date;
                        import java.sql.Date as Date;

                        class Example {
                        }
                        """)));
        assertTrue(failure.getMessage().contains("Alias 'Date' conflicts with the import 'java.util.Date'"),
                failure.getMessage());
    }

    @Test
    void unknownAlias() {
        AliasRegistry registry = JAlias.registry(ImportAlias.of("java.util.Date", "UtilDate"));
        AliasNotFoundException failure =
                assertThrows(AliasNotFoundException.class, () -> registry.require("UtilDtae"));
        assertTrue(failure.getMessage().contains("Unknown alias 'UtilDtae'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("UtilDate"), failure.getMessage());

        AliasNotFoundException viaReference = assertThrows(AliasNotFoundException.class,
                () -> TypeReference.of("UtilDtae").resolve(registry));
        assertTrue(viaReference.getMessage().contains("Unknown alias 'UtilDtae'"), viaReference.getMessage());
    }

    @Test
    void invalidImport() {
        InvalidImportException unresolvable = assertThrows(InvalidImportException.class, () -> TestHarness.compile(
                TestHarness.unit("Example", "import com.foo.Nope as Foo;\n\nclass Example {\n    Foo f;\n}\n")));
        assertTrue(unresolvable.getMessage().contains("Cannot resolve import 'com.foo.Nope'"),
                unresolvable.getMessage());

        InvalidImportException wildcard = assertThrows(InvalidImportException.class, () -> TestHarness.compile(
                TestHarness.unit("Example", "import com.foo.* as Foo;\n\nclass Example {\n}\n")));
        assertTrue(wildcard.getMessage().contains("Wildcard alias imports are not supported"),
                wildcard.getMessage());
    }

    @Test
    void circularResolution() {
        AliasRegistry registry = JAlias.registry(ImportAlias.of("com.foo.Bar", "FooBar"));
        CircularAliasException failure = assertThrows(CircularAliasException.class,
                () -> registry.register(ImportAlias.of("FooBar", "Bar")));
        assertTrue(failure.getMessage().contains("Circular alias resolution detected"), failure.getMessage());
    }

    @Test
    void invalidResolutionOfAnaliasedClass() {
        AliasRegistry registry = JAlias.registry(ImportAlias.of("com.foo.Bar", "FooBar"));
        InvalidImportException failure =
                assertThrows(InvalidImportException.class, () -> registry.resolveClass("FooBar"));
        assertTrue(failure.getMessage().contains("Cannot resolve aliased class 'com.foo.Bar'"),
                failure.getMessage());
    }

    @Test
    void malformedAliasSyntax() {
        AliasSyntaxException missingName = assertThrows(AliasSyntaxException.class, () -> JAlias.transform("""
                import com.foo.Bar as;

                class Example {
                }
                """));
        assertTrue(missingName.getMessage().contains("Missing alias name after 'as'"), missingName.getMessage());

        AliasSyntaxException keyword = assertThrows(AliasSyntaxException.class,
                () -> JAlias.transform("import com.foo.Bar as class;\nclass Example {}\n"));
        assertTrue(keyword.getMessage().contains("keywords cannot be used as aliases"), keyword.getMessage());
    }

    @Test
    void aliasUsedAsAMethodName() {
        AliasConflictException failure = assertThrows(AliasConflictException.class, () -> JAlias.transform("""
                import com.foo.Bar as FooBar;

                class Example extends Base {
                    void run() {
                        FooBar();
                    }
                }
                """));
        assertTrue(failure.getMessage().contains("Alias 'FooBar' is used as a method name"), failure.getMessage());
    }

    @Test
    void aliasShadowedByADeclaration() {
        AliasConflictException failure = assertThrows(AliasConflictException.class, () -> JAlias.transform("""
                import com.foo.Bar as FooBar;

                class Example {
                    void run() {
                        int FooBar = 1;
                    }
                }
                """));
        assertTrue(failure.getMessage().contains("conflicts with the variable 'FooBar'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("must not also be declared"), failure.getMessage());
    }

    @Test
    void aliasImportOutsideTheImportSection() {
        AliasSyntaxException failure = assertThrows(AliasSyntaxException.class, () -> JAlias.transform("""
                class Example {
                    import com.foo.Bar as FooBar;
                }
                """));
        assertTrue(failure.getMessage().contains("must be declared in the import section"), failure.getMessage());
    }

    @Test
    void everyAliasErrorExtendsTheBaseException() {
        assertTrue(new AliasConflictException("x") instanceof JAliasException);
        assertTrue(new AliasNotFoundException("x") instanceof JAliasException);
        assertTrue(new InvalidImportException("x") instanceof JAliasException);
        assertTrue(new AliasSyntaxException("x") instanceof JAliasException);
        assertTrue(new CircularAliasException("x") instanceof JAliasException);
        assertTrue(new AliasCompilationException("x") instanceof JAliasException);
        assertTrue(new JAliasException("x", new RuntimeException()) instanceof JAliasException);
    }

    @Test
    void unusableSourcesAreReportedAsCompilationExceptions() {
        AliasCompilationException failure = assertThrows(AliasCompilationException.class,
                () -> new AliasCompiler().transform(SourceUnit.of("Broken", """
                        import java.util.Date as UtilDate;

                        class Broken {
                            UtilDate created = new UtilDate(
                        }
                        """)));
        assertTrue(failure.getMessage().contains("Cannot parse"), failure.getMessage());
    }
}
