package io.jalias.resolver;

import io.jalias.exceptions.AliasConflictException;
import io.jalias.exceptions.CircularAliasException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.model.ImportAlias;
import io.jalias.model.TypeNames;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable registry of the aliases declared by one compilation unit.
 *
 * <p>The registry is where every conflict is detected and reported:
 *
 * <ul>
 *   <li>the same alias mapped to a different class:
 *       {@code Alias 'FooBar' is already mapped to com.foo.Bar}</li>
 *   <li>the same aliased import declared twice: {@code Duplicate aliased import: ...}</li>
 *   <li>an alias whose target is itself an alias:
 *       {@code Circular alias resolution detected: ...}</li>
 * </ul>
 *
 * <p>Registration is order sensitive and first-come-first-served: the first alias keeps its meaning
 * and every later conflict fails fast.
 */
public final class AliasRegistry implements AliasResolver {

    private final Map<String, ImportAlias> byAlias = new LinkedHashMap<>();

    /**
     * Registers an alias.
     *
     * @param alias the alias declaration to register
     * @return this registry, for chaining
     * @throws AliasConflictException if the alias is already mapped
     * @throws CircularAliasException if the alias or its target points at another alias
     */
    public AliasRegistry register(ImportAlias alias) {
        ImportAlias existing = byAlias.get(alias.alias());
        if (existing != null) {
            if (existing.qualifiedName().equals(alias.qualifiedName())) {
                throw new AliasConflictException("Duplicate aliased import: alias '" + alias.alias()
                        + "' is already mapped to " + existing.qualifiedName() + " (" + existing.location() + ")");
            }
            throw new AliasConflictException("Alias '" + alias.alias() + "' is already mapped to "
                    + existing.qualifiedName());
        }
        if (!TypeNames.isQualified(alias.qualifiedName()) && byAlias.containsKey(alias.qualifiedName())) {
            throw new CircularAliasException("Circular alias resolution detected: import target '"
                    + alias.qualifiedName() + "' is itself an alias for "
                    + byAlias.get(alias.qualifiedName()).qualifiedName() + " (declared in " + alias.location() + ")");
        }
        for (ImportAlias registered : byAlias.values()) {
            if (registered.qualifiedName().equals(alias.alias())) {
                throw new CircularAliasException("Circular alias resolution detected: '" + alias.alias()
                        + "' is the import target of '" + registered.alias() + "' (declared in "
                        + registered.location() + ")");
            }
        }
        byAlias.put(alias.alias(), alias);
        return this;
    }

    /**
     * Registers several aliases.
     *
     * @param aliases aliases to register
     * @return this registry, for chaining
     * @throws AliasConflictException if two aliases collide
     */
    public AliasRegistry registerAll(Collection<ImportAlias> aliases) {
        for (ImportAlias alias : aliases) {
            register(alias);
        }
        return this;
    }

    /**
     * Registers an alias only when the name is still free. Used to build a session wide view across
     * several compilation units, where the same alias may legitimately mean different classes in
     * different files.
     *
     * @param alias the alias declaration
     * @return this registry, for chaining
     */
    public AliasRegistry registerIfAbsent(ImportAlias alias) {
        byAlias.putIfAbsent(alias.alias(), alias);
        return this;
    }

    @Override
    public Optional<ImportAlias> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(byAlias.get(name));
    }

    @Override
    public Collection<ImportAlias> declaredAliases() {
        return byAlias.values();
    }

    /**
     * @return the alias names in registration order
     */
    public Set<String> aliasNames() {
        return new LinkedHashSet<>(byAlias.keySet());
    }

    /**
     * @return alias name to qualified class name, in registration order
     */
    public Map<String, String> mappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        byAlias.forEach((name, alias) -> mappings.put(name, alias.qualifiedName()));
        return mappings;
    }

    /**
     * @return the registered aliases, in registration order
     */
    public List<ImportAlias> aliasesInOrder() {
        return List.copyOf(byAlias.values());
    }

    /**
     * @return the number of registered aliases
     */
    public int size() {
        return byAlias.size();
    }

    /**
     * @return {@code true} when no alias is registered
     */
    public boolean isEmpty() {
        return byAlias.isEmpty();
    }

    /**
     * Removes all aliases.
     */
    public void clear() {
        byAlias.clear();
    }

    /**
     * Resolves an alias to the real class it stands for.
     *
     * @param alias       alias name
     * @param classLoader loader used to load the class
     * @return the class the alias refers to
     * @throws io.jalias.exceptions.AliasNotFoundException if the alias is unknown
     * @throws InvalidImportException                      if the class cannot be loaded
     */
    public Class<?> resolveClass(String alias, ClassLoader classLoader) {
        ImportAlias declaration = require(alias);
        ClassLoader loader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        try {
            return Class.forName(declaration.qualifiedName(), false, loader);
        } catch (ClassNotFoundException e) {
            throw new InvalidImportException("Cannot resolve aliased class '" + declaration.qualifiedName()
                    + "' for alias '" + alias + "'", e);
        }
    }

    /**
     * Resolves an alias to the real class it stands for using the context class loader.
     *
     * @param alias alias name
     * @return the class the alias refers to
     */
    public Class<?> resolveClass(String alias) {
        return resolveClass(alias, Thread.currentThread().getContextClassLoader());
    }

    @Override
    public String toString() {
        return "AliasRegistry" + mappings();
    }
}
