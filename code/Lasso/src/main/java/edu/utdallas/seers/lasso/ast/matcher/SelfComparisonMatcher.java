package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;

import java.util.List;

public class SelfComparisonMatcher extends PatternMatcher {

    private final ExpressionComparer expressionComparer = new ExpressionComparer();

    @Override
    public List<PatternInstance> visit(BinaryExpr n, ClassLocation arg) {
        return makePattern(n, arg,
                nn -> (nn.getOperator().equals(BinaryExpr.Operator.EQUALS) ||
                        nn.getOperator().equals(BinaryExpr.Operator.NOT_EQUALS)) &&
                        expressionComparer.areSame(nn.getLeft(), nn.getRight()),
                n.getLeft());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.SELF_COMPARISON;
    }
}
