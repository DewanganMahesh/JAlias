package io.jalias.resolver;

import io.jalias.exceptions.AliasNotFoundException;
import io.jalias.model.ImportAlias;
import io.jalias.model.TypeNames;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Read-only view of the aliases that are in scope for one compilation unit.
 *
 * <p>Keeping alias resolution behind this interface is what isolates the rest of JAlias from the
 * registry: the source transformer and the compiler only ever ask "what real class does this name
 * stand for?", they never mutate the set of aliases themselves.
 */
public interface AliasResolver {

    /**
     * Looks up an alias.
     *
     * @param name alias name
     * @return the alias declaration, or empty when the name is not an alias
     */
    Optional<ImportAlias> find(String name);

    /**
     * @return every alias known to this resolver
     */
    Collection<ImportAlias> declaredAliases();

    /**
     * Looks up an alias and fails when it is unknown.
     *
     * @param name alias name
     * @return the alias declaration
     * @throws AliasNotFoundException if the name is not a declared alias
     */
    default ImportAlias require(String name) {
        return find(name).orElseThrow(() -> new AliasNotFoundException(
                "Unknown alias '" + name + "'. Known aliases: " + aliases()));
    }

    /**
     * @param name name to check
     * @return {@code true} if the name is a declared alias
     */
    default boolean isAlias(String name) {
        return name != null && find(name).isPresent();
    }

    /**
     * @return the declared alias names, sorted, for messages and diagnostics
     */
    default List<String> aliases() {
        return declaredAliases().stream().map(ImportAlias::alias).sorted().toList();
    }

    /**
     * Resolves a name written in source code to the fully qualified name of a real class: aliases are
     * replaced by their target, qualified names are returned unchanged.
     *
     * @param name alias or qualified class name
     * @return the fully qualified class name
     * @throws AliasNotFoundException if the name is a simple name that is not a declared alias
     */
    default String resolveTypeName(String name) {
        ImportAlias alias = find(name).orElse(null);
        if (alias != null) {
            return alias.qualifiedName();
        }
        if (TypeNames.isQualified(name)) {
            return name;
        }
        throw new AliasNotFoundException("Unknown alias '" + name + "'. Known aliases: " + aliases());
    }

    /**
     * @return a resolver that knows no aliases
     */
    static AliasResolver empty() {
        return new DefaultAliasResolver(List.of());
    }

    /**
     * Creates an immutable resolver over the given aliases.
     *
     * @param aliases aliases in scope
     * @return the resolver
     */
    static AliasResolver of(Collection<ImportAlias> aliases) {
        return new DefaultAliasResolver(aliases);
    }

    /**
     * Creates an immutable resolver over the given aliases.
     *
     * @param aliases aliases in scope
     * @return the resolver
     */
    static AliasResolver of(ImportAlias... aliases) {
        return new DefaultAliasResolver(List.of(aliases));
    }
}
