package edu.utdallas.seers.lasso.data.entity;

import edu.utdallas.seers.lasso.ast.matcher.*;
import org.slf4j.LoggerFactory;

/**
 * Contains all implemented patterns.
 */
public enum PatternType {
    ASSIGN_CONSTANT(AssignConstantMatcher.class),
    BINARY_COMPARISON(BinaryComparisonMatcher.class),
    BINARY_FLAG_CHECK(BinaryFlagCheckMatcher.class),
    BOOLEAN_PROPERTY(BooleanPropertyMatcher.class),
    CONSTANT_ARGUMENT(ConstantArgumentMatcher.class),
    EQUALS_OR_CHAIN(EqualsOrChainMatcher.class),
    IF_CHAIN(IfChainMatcher.class),
    NULL_CHECK(NullCheckMatcher.class),
    NULL_EMPTY_CHECK(NullEmptyCheckMatcher.class),
    NULL_ZERO_CHECK(NullZeroCheckMatcher.class),
    RETURN_CONSTANT(ReturnConstantMatcher.class),
    SELF_COMPARISON(SelfComparisonMatcher.class),
    SWITCH_LEN_CHAR(SwitchLenCharMatcher.class);

    private final PatternMatcher matcher;

    static {
        // Checking that the matchers were correctly set
        var matchers = values();
        for (PatternType value : matchers) {
            assert value.equals(value.matcher.getPatternType());
        }

        LoggerFactory.getLogger(PatternType.class)
                .info("There are {} AST pattern matchers", matchers.length);
    }

    PatternType(Class<? extends PatternMatcher> theClass) {
        try {
            this.matcher = theClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PatternType fromString(String s) {
        try {
            return valueOf(s.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String toInputString() {
        return name().toLowerCase().replace('_', '-');
    }

    public PatternMatcher getMatcher() {
        return matcher;
    }
}
