package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.lasso.data.entity.constants.Constant;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class SwitchLenCharMatcher extends PatternMatcher {

    private final ConstantExtractor constantExtractor = new ConstantExtractor();

    // FIXME this is not discriminating enough, should have at least one nested switch
    @Override
    public List<PatternInstance> visit(SwitchStmt n, ClassLocation arg) {
        if (n.getParentNode()
                .orElseThrow(() -> new IllegalStateException("This node should have a parent"))
                instanceof SwitchEntry) {
            // It is nested inside another switch
            return Collections.emptyList();
        }

        // All labels must be integer constants
        Predicate<SwitchStmt> condition = nn -> nn.getEntries().stream()
                .allMatch(e -> e.getLabels().stream()
                        .allMatch(l -> constantExtractor.extractConstant(l, false)
                                .map(c -> c.getType() == Constant.Type.INTEGER)
                                .orElse(false)
                        ));

        // TODO revise these DDS
        return makePattern(n, arg, condition, n.getSelector());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.SWITCH_LEN_CHAR;
    }
}
