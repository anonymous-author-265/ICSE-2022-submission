package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.stmt.ReturnStmt;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.lasso.data.entity.ValueASTPattern;

import java.util.Collections;
import java.util.List;

public class ReturnConstantMatcher extends PatternMatcher {

    private final ConstantExtractor constantExtractor = new ConstantExtractor();

    @Override
    public List<PatternInstance> visit(ReturnStmt n, ClassLocation arg) {
        return n.getExpression()
                .flatMap(e -> constantExtractor.extractConstant(e, false))
                .map(c -> {
                    var newPattern = new ValueASTPattern(
                            arg.makePatternLocation(n),
                            PatternType.RETURN_CONSTANT,
                            makeOperands(true, n.getExpression().get()),
                            c
                    );
                    return new PatternInstance(n, newPattern, n.getExpression().get());
                })
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.RETURN_CONSTANT;
    }
}
