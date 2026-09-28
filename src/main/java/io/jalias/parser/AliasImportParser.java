package io.jalias.parser;

import io.jalias.exceptions.AliasSyntaxException;
import io.jalias.exceptions.InvalidImportException;
import io.jalias.model.CompilationUnitModel;
import io.jalias.model.ImportAlias;
import io.jalias.model.ImportStatement;
import io.jalias.model.LineIndex;
import io.jalias.model.SourceMap;
import io.jalias.model.SourceUnit;
import io.jalias.model.TypeNames;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the header of a compilation unit (package declaration plus imports) and recognises aliased
 * imports such as {@code import com.foo.Bar as FooBar;}.
 *
 * <p>Because Java has no {@code as} clause, the parser also produces a <em>normalised</em> copy of
 * the source in which every aliased import is turned into a comment occupying exactly the same
 * lines. That copy is valid Java, is what the compiler is asked to parse, and keeps every other
 * position in the file unchanged - the guarantee the rest of JAlias builds on.
 *
 * <p>The parser is deliberately forgiving about plain Java: it only fails for alias syntax problems.
 * Anything else (including plain imports that make no sense) is left to the Java compiler so that
 * developers get the diagnostics they are used to.
 */
public final class AliasImportParser {

    /**
     * Parses the given source text, guessing the binary name of the primary type.
     *
     * @param sourceText Java source, possibly using alias syntax
     * @return the parsed compilation unit
     */
    public CompilationUnitModel parse(String sourceText) {
        return parse(SourceUnit.of(sourceText));
    }

    /**
     * Parses the given compilation unit.
     *
     * @param unit the unit to parse
     * @return the parsed compilation unit
     * @throws AliasSyntaxException   if an aliased import is malformed
     * @throws InvalidImportException if an import cannot be understood by JAlias
     */
    public CompilationUnitModel parse(SourceUnit unit) {
        Objects.requireNonNull(unit, "unit");
        String source = unit.sourceText();
        LineIndex lines = new LineIndex(source);
        AliasSourceScanner scanner = new AliasSourceScanner(source);

        String packageName = "";
        List<ImportStatement> imports = new ArrayList<>();
        List<ImportAlias> aliases = new ArrayList<>();
        List<CompilationUnitModel.LineSpan> aliasSpans = new ArrayList<>();
        Map<Integer, String> editedLines = new LinkedHashMap<>();

        int pos = 0;
        while (true) {
            pos = scanner.skipTrivia(pos);
            if (pos >= source.length()) {
                break;
            }
            char current = source.charAt(pos);
            if (current == '@') {
                pos = scanner.skipAnnotation(pos);
                continue;
            }
            if (current == ';') {
                pos++;
                continue;
            }
            if (!scanner.isWordStart(pos)) {
                break;
            }
            if (scanner.wordAt(pos, "package")) {
                int end = scanner.findStatementEnd(pos);
                if (end < 0) {
                    throw new AliasSyntaxException("Unterminated package declaration in " + unit.displayName()
                            + " at line " + lines.lineNumber(pos));
                }
                packageName = readPackageName(source.substring(pos, end), unit, lines.lineNumber(pos));
                pos = end;
            } else if (scanner.wordAt(pos, "import")) {
                pos = readImport(unit, source, scanner, lines, pos, imports, aliases, aliasSpans, editedLines);
            } else {
                break;
            }
        }

        checkAliasSyntaxAfterHeader(scanner, pos, unit, lines);

        List<String> normalizedContents = lines.copyContents();
        editedLines.forEach(normalizedContents::set);
        String normalized = lines.reassemble(normalizedContents);

        LineIndex normalizedLines = new LineIndex(normalized);
        SourceMap sourceMap;
        if (editedLines.isEmpty()) {
            sourceMap = SourceMap.identity(source);
        } else {
            List<int[]> originalRanges = new ArrayList<>();
            List<int[]> normalizedRanges = new ArrayList<>();
            for (int line : editedLines.keySet()) {
                originalRanges.add(new int[] {lines.lineStart(line),
                        lines.lineStart(line) + lines.lineContent(line).length()});
                normalizedRanges.add(new int[] {normalizedLines.lineStart(line),
                        normalizedLines.lineStart(line) + normalizedLines.lineContent(line).length()});
            }
            sourceMap = SourceMap.of(source, normalized, originalRanges, normalizedRanges);
        }

        return new CompilationUnitModel(unit, packageName, imports, aliases, sourceMap, lines, normalizedLines,
                aliasSpans);
    }

    private int readImport(SourceUnit unit, String source, AliasSourceScanner scanner, LineIndex lines, int pos,
                           List<ImportStatement> imports, List<ImportAlias> aliases,
                           List<CompilationUnitModel.LineSpan> aliasSpans, Map<Integer, String> editedLines) {
        int end = scanner.findStatementEnd(pos);
        if (end < 0) {
            throw new AliasSyntaxException("Unterminated import statement in " + unit.displayName()
                    + " at line " + lines.lineNumber(pos) + ": missing ';'");
        }
        int line = lines.lineNumber(pos);
        int column = lines.columnNumber(pos);
        ImportStatement statement = parseImportStatement(source.substring(pos, end), unit.displayName(), line, column);
        imports.add(statement);
        if (statement.isAlias()) {
            ImportAlias parsed = statement.aliasTarget().orElseThrow();
            ImportAlias alias =
                    new ImportAlias(parsed.qualifiedName(), parsed.alias(), unit.displayName(), line, column);
            aliases.add(alias);
            int startLine = lines.lineOf(pos);
            int endLine = lines.lineOf(end - 1);
            aliasSpans.add(new CompilationUnitModel.LineSpan(startLine, endLine, alias));
            for (int edited = startLine; edited <= endLine; edited++) {
                editedLines.put(edited, commentOut(lines.lineContent(edited)));
            }
        }
        return end;
    }

    private ImportStatement parseImportStatement(String rawText, String fileName, int line, int column) {
        String display = oneLine(rawText);
        List<AliasSourceScanner.Token> tokens = new AliasSourceScanner(rawText).readTokens();
        if (tokens.isEmpty() || !tokens.get(0).text().equals("import")) {
            throw new InvalidImportException(
                    "Invalid import statement '" + display + "' in " + fileName + " at line " + line);
        }
        int index = 1;
        boolean isStatic = false;
        if (index < tokens.size() && tokens.get(index).text().equals("static")) {
            isStatic = true;
            index++;
        }
        if (index >= tokens.size()) {
            throw new InvalidImportException("Invalid import statement '" + display + "' in " + fileName
                    + " at line " + line + ": missing import target");
        }
        String target = tokens.get(index).text();
        boolean wildcard = target.equals("*") || target.endsWith(".*");
        String bare = wildcard ? (target.endsWith(".*") ? target.substring(0, target.length() - 2) : "") : target;
        int aliasIndex = index + 1;
        boolean hasAsClause = aliasIndex < tokens.size() && tokens.get(aliasIndex).text().equals("as");
        if (!hasAsClause) {
            if (aliasIndex != tokens.size()) {
                throw new InvalidImportException("Unexpected token '" + tokens.get(aliasIndex).text()
                        + "' in import statement '" + display + "' in " + fileName + " at line " + line);
            }
            if (isStatic) {
                String owner = wildcard || !TypeNames.isQualified(bare) ? bare : ownerOf(bare);
                String member = wildcard ? "*" : TypeNames.simpleNameOf(bare);
                return ImportStatement.staticImport(owner, member, wildcard, rawText, line, column);
            }
            if (wildcard) {
                return ImportStatement.wildcard(bare, rawText, line, column);
            }
            if (!TypeNames.isValidQualifiedName(bare)) {
                throw new InvalidImportException("Invalid import target '" + target + "' in " + fileName
                        + " at line " + line + ": expected a qualified class name");
            }
            return ImportStatement.single(bare, rawText, line, column);
        }
        if (aliasIndex + 1 >= tokens.size()) {
            throw new AliasSyntaxException("Missing alias name after 'as' in '" + display + "' in " + fileName
                    + " at line " + line);
        }
        String aliasName = tokens.get(aliasIndex + 1).text();
        if (aliasIndex + 2 != tokens.size()) {
            throw new AliasSyntaxException("Unexpected token '" + tokens.get(aliasIndex + 2).text() + "' after alias '"
                    + aliasName + "' in '" + display + "' in " + fileName + " at line " + line);
        }
        if (isStatic) {
            throw new InvalidImportException("Static alias imports are not supported: '" + display + "' in " + fileName
                    + " at line " + line + ". Aliases can only refer to types; use 'import " + bare + " as " + aliasName
                    + ";' or a plain static import");
        }
        if (wildcard) {
            throw new InvalidImportException("Wildcard alias imports are not supported: '" + display + "' in " + fileName
                    + " at line " + line + ". An alias can only refer to a single type");
        }
        ImportAlias alias = new ImportAlias(bare, aliasName, fileName, line, column);
        return ImportStatement.alias(alias, rawText, line, column);
    }

    private void checkAliasSyntaxAfterHeader(AliasSourceScanner scanner, int from, SourceUnit unit, LineIndex lines) {
        int index = scanner.indexOfKeyword("import", from);
        while (index >= 0) {
            int end = scanner.findStatementEnd(index);
            if (end < 0) {
                return;
            }
            String rawText = scanner.text().substring(index, end);
            boolean hasAsClause = new AliasSourceScanner(rawText).readTokens().stream()
                    .anyMatch(token -> token.text().equals("as"));
            if (hasAsClause) {
                throw new AliasSyntaxException("Aliased import must be declared in the import section, before the "
                        + "first type declaration: '" + oneLine(rawText) + "' in " + unit.displayName() + " at line "
                        + lines.lineNumber(index));
            }
            index = scanner.indexOfKeyword("import", end);
        }
    }

    private String readPackageName(String statement, SourceUnit unit, int line) {
        List<AliasSourceScanner.Token> tokens = new AliasSourceScanner(statement).readTokens();
        if (tokens.size() >= 2 && tokens.get(0).text().equals("package")) {
            String candidate = tokens.get(1).text();
            if (TypeNames.isValidQualifiedName(candidate)) {
                return candidate;
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("package\\s+([\\w.$]+)\\s*;")
                .matcher(oneLine(statement));
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new AliasSyntaxException("Invalid package declaration '" + oneLine(statement) + "' in "
                + unit.displayName() + " at line " + line);
    }

    /**
     * Turns a line of source into a comment of exactly the same length. Blank lines stay blank.
     */
    private static String commentOut(String lineContent) {
        if (lineContent.isBlank()) {
            return lineContent;
        }
        if (lineContent.length() >= 2) {
            return "//" + lineContent.substring(2);
        }
        return "//";
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * @param qualifiedName a qualified name with at least two segments
     * @return everything before the last segment
     */
    private static String ownerOf(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? qualifiedName : qualifiedName.substring(0, lastDot);
    }
}
