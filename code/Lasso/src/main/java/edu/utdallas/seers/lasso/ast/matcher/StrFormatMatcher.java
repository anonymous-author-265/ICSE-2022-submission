package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.expr.MethodCallExpr;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;

import java.util.List;

public class StrFormatMatcher extends PatternMatcher {
    @Override
    public List<PatternInstance> visit(MethodCallExpr n, ClassLocation arg) {
        return makePattern(n, arg, nn -> nn.getName().toString().equals("format"));
    }

    @Override
    public PatternType getPatternType() {
        return null;
    }
}
