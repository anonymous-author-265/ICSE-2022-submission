package edu.utdallas.seers.lasso.data.entity;

import edu.utdallas.seers.lasso.ast.PatternStore;
import edu.utdallas.seers.lasso.data.entity.constants.Constant;

import java.util.List;
import java.util.Objects;

public class ValueASTPattern extends ASTPattern {

    private final Constant<?> constant;

    public ValueASTPattern(Location location, PatternType patternType,
                           List<PatternOperand> operands, Constant<?> constant) {
        super(location, patternType, operands);
        this.constant = constant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ValueASTPattern that = (ValueASTPattern) o;
        return constant.equals(that.constant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), constant);
    }

    @Override
    public void accept(PatternStore store) {
        store.addPattern(this);
    }

    public Constant<?> getConstant() {
        return constant;
    }
}
