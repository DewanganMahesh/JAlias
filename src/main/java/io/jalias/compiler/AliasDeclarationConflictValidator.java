package io.jalias.compiler;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import io.jalias.exceptions.AliasConflictException;
import io.jalias.model.AliasOptions;
import io.jalias.model.LineIndex;

import javax.lang.model.element.Name;
import java.util.Set;

/**
 * Rejects sources in which an alias name is also used as a declared name.
 *
 * <p>Aliases are pure name replacements. If a file declared a local variable, parameter, method,
 * label, nested type or type parameter with the same name as one of its aliases, the two meanings
 * could not be told apart reliably, so JAlias refuses the file with an explicit message instead of
 * guessing. {@link AliasOptions#allowDeclarationNameConflicts()} turns the check off for people who
 * know the source is unambiguous.
 */
final class AliasDeclarationConflictValidator {

    private AliasDeclarationConflictValidator() {
    }

    /**
     * Scans the compilation unit for declarations that collide with an alias.
     *
     * @param unit       parsed compilation unit
     * @param positions  position service of the parsing task
     * @param lines      line index of the parsed (normalised) source
     * @param aliasNames aliases declared by the unit
     * @param fileName   display name used in messages
     * @param options    effective options
     * @throws AliasConflictException if a declared name collides with an alias
     */
    static void validate(CompilationUnitTree unit, SourcePositions positions, LineIndex lines,
                         Set<String> aliasNames, String fileName, AliasOptions options) {
        if (aliasNames.isEmpty() || options.allowDeclarationNameConflicts()) {
            return;
        }
        new TreeScanner<Void, Void>() {

            @Override
            public Void visitClass(ClassTree node, Void unused) {
                check(node.getSimpleName(), "type", node);
                return super.visitClass(node, unused);
            }

            @Override
            public Void visitMethod(MethodTree node, Void unused) {
                check(node.getName(), "method", node);
                return super.visitMethod(node, unused);
            }

            @Override
            public Void visitVariable(VariableTree node, Void unused) {
                check(node.getName(), "variable", node);
                return super.visitVariable(node, unused);
            }

            @Override
            public Void visitTypeParameter(TypeParameterTree node, Void unused) {
                check(node.getName(), "type parameter", node);
                return super.visitTypeParameter(node, unused);
            }

            @Override
            public Void visitLabeledStatement(LabeledStatementTree node, Void unused) {
                check(node.getLabel(), "label", node);
                return super.visitLabeledStatement(node, unused);
            }

            private void check(Name name, String kind, Tree node) {
                if (name == null || name.isEmpty()) {
                    return;
                }
                String text = name.toString();
                if (text.startsWith("<") || !aliasNames.contains(text)) {
                    return;
                }
                long offset = positions.getStartPosition(unit, node);
                int line = offset < 0 ? 0 : lines.lineNumber((int) offset);
                throw new AliasConflictException("Alias '" + text + "' conflicts with the " + kind + " '" + text
                        + "' declared at line " + line + " in " + fileName
                        + ": an alias name must not also be declared in the same file");
            }
        }.scan(unit, null);
    }
}
