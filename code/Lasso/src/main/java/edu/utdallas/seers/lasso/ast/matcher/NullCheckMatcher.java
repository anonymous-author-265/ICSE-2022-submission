package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import org.jooq.lambda.tuple.Tuple2;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * TODO does not accept a.equals(null)
 */
public class NullCheckMatcher extends PatternMatcher {
    Optional<Expression> findNullCheckOperand(BinaryExpr binaryExpr) {
        if (!binaryExpr.getOperator().equals(BinaryExpr.Operator.EQUALS) &&
                !binaryExpr.getOperator().equals(BinaryExpr.Operator.NOT_EQUALS)) {
            return Optional.empty();
        }

        var pair = tuple(binaryExpr.getLeft(), binaryExpr.getRight());
        return Stream.of(pair, pair.swap())
                .filter(t -> t.v1.isNullLiteralExpr())
                .map(Tuple2::v2)
                .findFirst();
    }

    @Override
    public List<PatternInstance> visit(BinaryExpr n, ClassLocation arg) {
        return findNullCheckOperand(n)
                .map(e -> makePattern(n, arg, nn -> true, e))
                .orElse(Collections.emptyList());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.NULL_CHECK;
    }
}
