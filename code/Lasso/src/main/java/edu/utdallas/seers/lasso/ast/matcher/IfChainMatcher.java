package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.PatternType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class IfChainMatcher extends PatternMatcher {
    /**
     * Only check the chain if the current node is the first one in order to make the matching
     * unambiguous.
     *
     * @param ifStatement Statement to check.
     * @return {@code true} if the statement is not nested.
     */
    private boolean isFirst(IfStmt ifStatement) {
        return ifStatement.getParentNode()
                .map(n -> !(n instanceof IfStmt))
                .orElseThrow();
    }

    private Stream<Expression> findConditions(IfStmt root) {
        return Stream.concat(
                Stream.of(root.getCondition()),
                root.getElseStmt().flatMap(Statement::toIfStmt)
                        .stream()
                        .flatMap(this::findConditions)
        );
    }

    @Override
    public List<PatternInstance> visit(IfStmt n, ClassLocation arg) {
        if (!isFirst(n)) {
            return Collections.emptyList();
        }

        // TODO maybe figure out a condition for filtering the matching based on the last else in the chain, could be block but other statements are valid
        // TODO make it so that the pattern lines are only those corresponding to the conditions
        // TODO check that there is at least one operand shared between all conditions
        return n.getElseStmt()
                // We only require one nested if
                .filter(Statement::isIfStmt)
                .map(e -> {
                    var rawOperands = findConditions(n).limit(2).toArray(Node[]::new);
                    var newPattern = new ASTPattern(
                            arg.makePatternLocation(n),
                            PatternType.IF_CHAIN,
                            // TODO using at most 2 conds because the operands to this pattern do not behave the same
                            //   and we need to restrict the amount of operands for indexing (can instead concatenate ops for now, but figure out something better)
                            makeOperands(true, rawOperands));

                    return new PatternInstance(n, newPattern, rawOperands);
                })
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.IF_CHAIN;
    }
}
