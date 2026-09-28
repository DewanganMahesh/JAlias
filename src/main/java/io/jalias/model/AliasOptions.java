package io.jalias.model;

/**
 * Knobs that control how strict JAlias is and what the generated Java source looks like.
 *
 * @param verifyImportTargets          check with the compiler that the target of every aliased
 *                                     import really exists (default {@code true}); when disabled the
 *                                     problem surfaces as a normal compiler error instead
 * @param checkVisibleTypeConflicts    reject aliases that collide with a type that is visible in
 *                                     the compilation unit, for example {@code String} or a type
 *                                     imported with a plain import (default {@code true})
 * @param emitAliasComments            keep the aliased imports in the generated source as comments
 *                                     showing the original declaration, which makes the
 *                                     transformation easy to trace; when disabled the alias lines are
 *                                     blanked out instead (the generated source stays valid Java
 *                                     either way, default {@code true})
 * @param allowDeclarationNameConflicts allow an alias whose name is also declared as a variable,
 *                                     method, label, nested type or type parameter in the same
 *                                     file (default {@code false}, see the README for why this is
 *                                     refused: the two meanings could not be told apart safely)
 */
public record AliasOptions(boolean verifyImportTargets,
                           boolean checkVisibleTypeConflicts,
                           boolean emitAliasComments,
                           boolean allowDeclarationNameConflicts) {

    /**
     * The default behaviour: strict checks, traceable generated sources.
     */
    public static final AliasOptions DEFAULT = new AliasOptions(true, true, true, false);

    /**
     * @return the default options
     */
    public static AliasOptions defaults() {
        return DEFAULT;
    }

    /**
     * @param value new value
     * @return a copy of these options with {@code verifyImportTargets} replaced
     */
    public AliasOptions withVerifyImportTargets(boolean value) {
        return new AliasOptions(value, checkVisibleTypeConflicts, emitAliasComments, allowDeclarationNameConflicts);
    }

    /**
     * @param value new value
     * @return a copy of these options with {@code checkVisibleTypeConflicts} replaced
     */
    public AliasOptions withCheckVisibleTypeConflicts(boolean value) {
        return new AliasOptions(verifyImportTargets, value, emitAliasComments, allowDeclarationNameConflicts);
    }

    /**
     * @param value new value
     * @return a copy of these options with {@code emitAliasComments} replaced
     */
    public AliasOptions withEmitAliasComments(boolean value) {
        return new AliasOptions(verifyImportTargets, checkVisibleTypeConflicts, value, allowDeclarationNameConflicts);
    }

    /**
     * @param value new value
     * @return a copy of these options with {@code allowDeclarationNameConflicts} replaced
     */
    public AliasOptions withAllowDeclarationNameConflicts(boolean value) {
        return new AliasOptions(verifyImportTargets, checkVisibleTypeConflicts, emitAliasComments, value);
    }
}
