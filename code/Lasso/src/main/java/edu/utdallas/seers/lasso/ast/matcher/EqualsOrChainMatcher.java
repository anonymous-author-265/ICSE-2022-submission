package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.printer.PrettyPrinter;
import com.google.common.collect.Maps;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO converts nodes into strings to determine if there is a common element in all equalities
 * in the chain. This might not be exhaustive enough.
 */
public class EqualsOrChainMatcher extends PatternMatcher {
    /**
     * Using this printer instead of Node.toString because that method can yield different results
     * if configuration changes.
     */
    private final PrettyPrinter stringConverter = new PrettyPrinter();

    /**
     * Each component of the chain must be a binary expression where one side is an equality and
     * the other is either (1) a {@link BinaryExpr} using the {@code ||} operator or (2) another
     * equality. This method unravels the chain if it is valid.
     * <p>
     * TODO: this would not work for expressions like (a == 0 || a == 1) || (a == 2 || a == 3)
     *
     * @param expression Candidate equals-or-chain.
     * @return Operand if expression is a valid chain.
     */
    private Optional<Expression> findOperand(BinaryExpr expression) {
        // Finds the operand in the chain, if it is valid
        var equalities = unravelChain(expression);

        /* Since a chain must contain at least 2 equalities and a single equality is a valid input
        to unravel chain link */
        if (equalities.size() < 2) {
            return Optional.empty();
        }

        return equalities.stream()
                .map(e -> e.toMap(stringConverter))
                .reduce((m1, m2) -> Maps.difference(m1, m2).entriesInCommon())
                // TODO returns the first one, but we might want to check that there is only one
                .flatMap(m -> m.values().stream().findFirst());
    }

    private List<Equality> unravelChain(Expression expression) {
        return Equality.extract(expression)
                .map(Collections::singletonList)
                .orElseGet(() -> expression.toBinaryExpr()
                        .filter(e -> e.getOperator().equals(BinaryExpr.Operator.OR))
                        .map(e -> {
                            var eqs = Stream.of(e.getLeft(), e.getRight())
                                    .map(this::unravelChain)
                                    // If either is empty, there is no chain
                                    .takeWhile(es -> !es.isEmpty())
                                    .collect(Collectors.toList());

                            return Optional.of(eqs)
                                    .filter(cs -> cs.size() == 2)
                                    .map(cs -> cs.stream().flatMap(Collection::stream))
                                    .orElse(Stream.empty())
                                    .collect(Collectors.toList());
                        })
                        .orElse(Collections.emptyList()));
    }

    /**
     * Only accept a candidate if it is the largest possible chain, in order to make the matching
     * unambiguous.
     *
     * @param expression Expression to test.
     * @return true if the expression is the largest possible {@link BinaryExpr} chain.
     */
    private boolean isLargest(Expression expression) {
        var parentNode = expression.getParentNode()
                .orElseThrow(() -> new IllegalStateException("This node should have a parent"));

        return Optional.of(parentNode)
                .filter(n -> n instanceof Expression)
                .map(n -> {
                    var parentExpr = (Expression) n;
                    return parentExpr.toEnclosedExpr().map(this::isLargest).orElse(false) ||
                            !parentExpr.isBinaryExpr();
                })
                .orElse(true);
    }

    @Override
    public List<PatternInstance> visit(BinaryExpr n, ClassLocation arg) {
        return Optional.of(n)
                .filter(this::isLargest)
                .flatMap(this::findOperand)
                .map(e -> makePattern(n, arg, nn -> true, e))
                .orElse(Collections.emptyList());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.EQUALS_OR_CHAIN;
    }

    static class Equality {

        private final Expression first;
        private final Expression second;

        private Equality(Expression first, Expression second) {
            this.first = first;
            this.second = second;
        }

        /**
         * Extracts an equality from an expression if the expression is a valid equality.
         * Accepts a == b and a.equals(b).
         *
         * @param expression Expression to extract from.
         * @return An equality object, if the expression is valid.
         */
        static Optional<Equality> extract(Expression expression) {
            return expression.toBinaryExpr()
                    .filter(e -> e.getOperator().equals(BinaryExpr.Operator.EQUALS))
                    .map(e -> new Equality(e.getLeft(), e.getRight()))
                    .or(() -> expression.toMethodCallExpr()
                            // Accepting methods like "equalsIgnoreCase" as long as there is only one argument
                            .filter(e -> e.getName().toString().startsWith("equals") &&
                                    e.getArguments().size() == 1)
                            .flatMap(e -> e.getScope()
                                    .map(s -> new Equality(s, e.getArgument(0)))
                            )
                    );
        }

        /**
         * Turns both sides of the equality into strings and returns them as a map.
         *
         * @param stringConverter To convert nodes into strings.
         * @return A map of two expressions.
         */
        Map<String, Expression> toMap(PrettyPrinter stringConverter) {
            return Stream.of(first, second)
                    .collect(Collectors.toMap(
                            stringConverter::print,
                            e -> e,
                            // Sometimes we see stuff like d == d
                            (s1, s2) -> s1
                    ));
        }
    }

}
