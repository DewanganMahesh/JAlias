package io.jalias.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parsed view of one compilation unit: its imports, the aliases it declares, the source as
 * written, and the normalised source that the Java compiler can actually parse.
 *
 * <p>Instances are created by {@link io.jalias.parser.AliasImportParser}; the class itself is an
 * immutable data holder.
 */
public final class CompilationUnitModel {

    /**
     * A range of lines occupied by an aliased import.
     *
     * @param startLine zero based first line
     * @param endLine   zero based last line, inclusive
     * @param alias     the alias declared by those lines
     */
    public record LineSpan(int startLine, int endLine, ImportAlias alias) {
    }

    private final SourceUnit source;
    private final String packageName;
    private final List<ImportStatement> imports;
    private final List<ImportAlias> aliases;
    private final SourceMap sourceMap;
    private final LineIndex lineIndex;
    private final LineIndex normalizedLineIndex;
    private final List<LineSpan> aliasLineSpans;

    /**
     * Creates the model.
     *
     * @param source              the source unit
     * @param packageName         the package declared by the unit, empty for the unnamed package
     * @param imports             every import statement, in source order
     * @param aliases             every aliased import, in source order
     * @param sourceMap           mapping between original and normalised offsets
     * @param lineIndex           line index of the original source
     * @param normalizedLineIndex line index of the normalised source
     * @param aliasLineSpans      line ranges of the aliased imports
     */
    public CompilationUnitModel(SourceUnit source, String packageName, List<ImportStatement> imports,
                                List<ImportAlias> aliases, SourceMap sourceMap, LineIndex lineIndex,
                                LineIndex normalizedLineIndex, List<LineSpan> aliasLineSpans) {
        this.source = source;
        this.packageName = packageName == null ? "" : packageName;
        this.imports = List.copyOf(imports);
        this.aliases = List.copyOf(aliases);
        this.sourceMap = sourceMap;
        this.lineIndex = lineIndex;
        this.normalizedLineIndex = normalizedLineIndex;
        this.aliasLineSpans = List.copyOf(aliasLineSpans);
    }

    /**
     * @return the source unit this model was parsed from
     */
    public SourceUnit source() {
        return source;
    }

    /**
     * @return the package name, empty when the unit is in the unnamed package
     */
    public String packageName() {
        return packageName;
    }

    /**
     * @return every import statement in source order
     */
    public List<ImportStatement> imports() {
        return imports;
    }

    /**
     * @return every aliased import in source order
     */
    public List<ImportAlias> aliases() {
        return aliases;
    }

    /**
     * @return the imports that JAlias leaves alone, i.e. everything except aliased imports
     */
    public List<ImportStatement> plainImports() {
        return imports.stream().filter(statement -> !statement.isAlias()).toList();
    }

    /**
     * @return {@code true} if the unit declares at least one alias
     */
    public boolean hasAliases() {
        return !aliases.isEmpty();
    }

    /**
     * @return the alias names declared by the unit
     */
    public Set<String> aliasNames() {
        return aliases.stream().map(ImportAlias::alias).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Looks up an alias declared by this unit.
     *
     * @param name alias name
     * @return the alias declaration, or empty
     */
    public Optional<ImportAlias> alias(String name) {
        return aliases.stream().filter(alias -> alias.alias().equals(name)).findFirst();
    }

    /**
     * @return alias name to qualified class name, in declaration order
     */
    public Map<String, String> aliasMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (ImportAlias alias : aliases) {
            mappings.put(alias.alias(), alias.qualifiedName());
        }
        return mappings;
    }

    /**
     * @return the mapping between original and normalised offsets
     */
    public SourceMap sourceMap() {
        return sourceMap;
    }

    /**
     * @return the source handed to the Java compiler: every aliased import replaced by a comment of the
     *         same length, everything else untouched
     */
    public String normalizedSource() {
        return sourceMap.normalizedText();
    }

    /**
     * @return the source exactly as it was written
     */
    public String originalSource() {
        return sourceMap.originalText();
    }

    /**
     * @return the line index of the original source text
     */
    public LineIndex lineIndex() {
        return lineIndex;
    }

    /**
     * @return the line index of the normalised source text, which has the same line structure as
     *         the original
     */
    public LineIndex normalizedLineIndex() {
        return normalizedLineIndex;
    }

    /**
     * @return the line ranges of the aliased imports
     */
    public List<LineSpan> aliasLineSpans() {
        return aliasLineSpans;
    }
}
