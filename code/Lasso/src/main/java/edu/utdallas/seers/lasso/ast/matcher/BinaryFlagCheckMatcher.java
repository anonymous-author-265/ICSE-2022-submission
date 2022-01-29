package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BinaryFlagCheckMatcher extends PatternMatcher {
    private final Set<BinaryExpr.Operator> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            BinaryExpr.Operator.BINARY_AND, BinaryExpr.Operator.BINARY_OR, BinaryExpr.Operator.XOR
    ));

    @Override
    public List<PatternInstance> visit(BinaryExpr n, ClassLocation arg) {
        return makePattern(n, arg, nn -> VALID_OPERATORS.contains(nn.getOperator()),
                n.getLeft(), n.getRight());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.BINARY_FLAG_CHECK;
    }
}
