package io.jalias.imports;

import io.jalias.exceptions.AliasConflictException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.ImportAlias;
import io.jalias.model.ImportKind;
import io.jalias.model.ImportStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Answers questions about the plain imports of a compilation unit.
 *
 * <p>JAlias never rewrites plain imports: they keep working exactly as in Java, including wildcard
 * and static imports. This class exists for the two places where plain imports and aliases interact:
 * resolving a simple name for {@link io.jalias.model.TypeReference} and detecting collisions between
 * an alias and a name a plain import already introduced.
 */
public final class ImportResolver {

    /**
     * Resolves a simple name through the single type import statements of the unit.
     *
     * @param simpleName simple name such as {@code Bar}
     * @param unit       the unit whose imports are consulted
     * @return the qualified name the import introduces, or empty when no single import matches
     *         (a wildcard import is reported as unresolved because JAlias does not read class files)
     * @throws InvalidImportException if more than one import introduces the same simple name
     */
    public Optional<String> resolveSimpleName(String simpleName, CompilationUnitModel unit) {
        List<ImportStatement> matches = new ArrayList<>();
        for (ImportStatement statement : unit.plainImports()) {
            if (statement.importedSimpleName().filter(simpleName::equals).isPresent()) {
                matches.add(statement);
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            String owners = matches.stream().map(ImportStatement::qualifiedName)
                    .reduce((left, right) -> left + " and " + right).orElse("");
            throw new InvalidImportException("Ambiguous import of '" + simpleName + "': " + owners);
        }
        return Optional.of(matches.get(0).qualifiedName());
    }

    /**
     * @param unit the unit to inspect
     * @return the simple names introduced by single type imports
     */
    public List<String> importedSimpleNames(CompilationUnitModel unit) {
        List<String> names = new ArrayList<>();
        for (ImportStatement statement : unit.plainImports()) {
            statement.importedSimpleName().ifPresent(names::add);
        }
        return names;
    }

    /**
     * Checks that no alias shadows a name that a plain import already introduced.
     *
     * @param unit the unit to check
     * @throws AliasConflictException if an alias collides with an import
     */
    public void checkAliasConflicts(CompilationUnitModel unit) {
        for (ImportAlias alias : unit.aliases()) {
            for (ImportStatement statement : unit.plainImports()) {
                if (statement.kind() == ImportKind.SINGLE && statement.name().equals(alias.alias())) {
                    throw new AliasConflictException("Alias '" + alias.alias() + "' conflicts with the import '"
                            + statement.qualifiedName() + "' at line " + statement.line() + " ("
                            + alias.location() + ")");
                }
                if (statement.isStatic() && !statement.isWildcard() && statement.name().equals(alias.alias())) {
                    throw new AliasConflictException("Alias '" + alias.alias() + "' conflicts with the static import '"
                            + statement.qualifiedName() + "." + statement.name() + "' at line " + statement.line()
                            + " (" + alias.location() + ")");
                }
            }
        }
    }

    /**
     * @param unit the unit to inspect
     * @return the import statements of the unit that JAlias leaves untouched
     */
    public List<ImportStatement> plainImports(CompilationUnitModel unit) {
        return unit.plainImports();
    }
}
