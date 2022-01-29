package edu.utdallas.seers.lasso.data.entity;

import edu.utdallas.seers.lasso.ast.PatternStore;
import edu.utdallas.seers.lasso.data.entity.constants.Constant;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;

import java.util.List;
import java.util.Objects;

/**
 * Has a value and a name, which can be a field name, method name, variable name...
 */
public class NameValueASTPattern extends ValueASTPattern {

    private final Attribute attribute;

    public NameValueASTPattern(Location location, PatternType patternType,
                               List<PatternOperand> operands, Constant<?> constant, Attribute attribute) {
        super(location, patternType, operands, constant);
        this.attribute = attribute;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        NameValueASTPattern that = (NameValueASTPattern) o;
        return attribute.equals(that.attribute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), attribute);
    }

    @Override
    public void accept(PatternStore store) {
        store.addPattern(this);
    }

    public Attribute getVariable() {
        return attribute;
    }
}
