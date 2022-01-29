package edu.utdallas.seers.lasso.data.entity;

import org.jooq.lambda.Seq;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public enum ConstraintType {
    VALUE_COMPARISON(Collections.singletonList(PatternType.BINARY_COMPARISON)),
    DUAL_VALUE_COMPARISON(Arrays.asList(PatternType.BOOLEAN_PROPERTY, PatternType.NULL_CHECK)),
    CONCRETE_VALUE(Arrays.asList(PatternType.CONSTANT_ARGUMENT, PatternType.ASSIGN_CONSTANT)),
    CATEGORICAL_VALUE(Collections.singletonList(PatternType.IF_CHAIN));

    private final List<PatternType> expectedPatternTypes;

    ConstraintType(List<PatternType> expectedPatternTypes) {
        this.expectedPatternTypes = expectedPatternTypes;
    }

    public static ConstraintType fromString(String s) {
        return ConstraintType.valueOf(s.toUpperCase()
                .replace('-', '_')
                .replace(' ', '_')
        );
    }

    public String toInputString() {
        return this.name().toLowerCase().replace("_", "-");
    }

    public Map<PatternType, Float> getPatternTypeBoosts() {
        return Seq.zipWithIndex(expectedPatternTypes)
                .toMap(t -> t.v1, t -> t.v2 == 0 ? 0.2f : 0.1f);
    }

    public List<PatternType> getExpectedPatternTypes() {
        return expectedPatternTypes;
    }
}
