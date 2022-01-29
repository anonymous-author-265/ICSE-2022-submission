package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;

import java.util.*;
import java.util.stream.Stream;

public class BinaryComparisonMatcher extends PatternMatcher {
    private final Set<BinaryExpr.Operator> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            BinaryExpr.Operator.OR, BinaryExpr.Operator.AND, BinaryExpr.Operator.EQUALS,
            BinaryExpr.Operator.NOT_EQUALS, BinaryExpr.Operator.LESS, BinaryExpr.Operator.GREATER,
            BinaryExpr.Operator.LESS_EQUALS, BinaryExpr.Operator.GREATER_EQUALS
    ));

    private final ExpressionComparer comparer = new ExpressionComparer();

    @Override
    public List<PatternInstance> visit(BinaryExpr n, ClassLocation arg) {
        return makePattern(n, arg,
                // TODO come up with better way of avoiding two matchers matching same expression: self-comp and null-check in this case
                nn -> (!nn.getLeft().isNullLiteralExpr() && !nn.getRight().isNullLiteralExpr()) &&
                        !comparer.areSame(nn.getLeft(), nn.getRight()) &&
                        VALID_OPERATORS.contains(nn.getOperator()),
                n.getLeft(), n.getRight());
    }

    @Override
    public List<PatternInstance> visit(MethodCallExpr n, ClassLocation arg) {
        // TODO could use resolution here to make sure it is the correct equals method
        var operands = Stream.concat(
                n.getScope().stream(),
                Optional.of(n.getArguments()).filter(ns -> !ns.isEmpty()).map(ns -> ns.get(0)).stream()
        )
                .toArray(Node[]::new);

        return makePattern(n, arg,
                // Don't need to check same operands here because d.equals(d) would make no sense
                nn -> nn.getName().toString().equals("equals") && nn.getArguments().size() == 1,
                operands);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.BINARY_COMPARISON;
    }
}
