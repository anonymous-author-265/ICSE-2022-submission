package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import edu.utdallas.seers.lasso.ast.ClassLocation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Base class for matchers of the type null-*-check.
 */
abstract class AugmentedNullCheckMatcher extends PatternMatcher {
    private final NullCheckMatcher nullCheckMatcher = new NullCheckMatcher();
    private final ExpressionComparer expressionComparer = new ExpressionComparer();

    /**
     * Tries to find the operand in the expression next to the null check that must match the one
     * used for the null check for this pattern to apply.
     *
     * @param expression Expression to check.
     * @return The operand in the expression that conforms to the pattern, or empty if no match.
     */
    protected abstract Optional<? extends Expression> extractOtherOperand(Expression expression);

    @Override
    public List<PatternInstance> visit(BinaryExpr n, ClassLocation arg) {
        if (!n.getOperator().equals(BinaryExpr.Operator.OR) &&
                !n.getOperator().equals(BinaryExpr.Operator.AND)) {
            return Collections.emptyList();
        }

        // Null check must always be the left operation
        var maybeOperand = n.getLeft().toBinaryExpr()
                .flatMap(nullCheckMatcher::findNullCheckOperand);

        if (maybeOperand.isEmpty()) return Collections.emptyList();

        var nonNullOperand = maybeOperand.get();

        return extractOtherOperand(n.getRight())
                // TODO can use resolution to compare the expressions instead of this
                .map(e -> makePattern(n, arg, o -> expressionComparer.areSame(nonNullOperand, e), nonNullOperand))
                .orElse(Collections.emptyList());
    }
}
