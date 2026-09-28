package io.jalias.resolver;

import io.jalias.model.ImportAlias;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable {@link AliasResolver} over a snapshot of aliases.
 *
 * <p>Snapshots are used by the source transformer so that a transformation can never observe a
 * registry that is being modified concurrently.
 */
public final class DefaultAliasResolver implements AliasResolver {

    private final Map<String, ImportAlias> byAlias;

    /**
     * Creates the resolver.
     *
     * @param aliases aliases in scope; when the same alias appears twice the last one wins, so callers
     *                that need conflict detection should use {@link AliasRegistry}
     */
    public DefaultAliasResolver(Collection<ImportAlias> aliases) {
        Objects.requireNonNull(aliases, "aliases");
        Map<String, ImportAlias> map = new LinkedHashMap<>();
        for (ImportAlias alias : aliases) {
            map.put(alias.alias(), alias);
        }
        this.byAlias = Map.copyOf(map);
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
     * @return an empty resolver
     */
    public static DefaultAliasResolver empty() {
        return new DefaultAliasResolver(java.util.List.of());
    }

    @Override
    public String toString() {
        return "DefaultAliasResolver" + aliases();
    }
}
