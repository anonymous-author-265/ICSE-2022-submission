package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.PatternOperand;
import edu.utdallas.seers.lasso.data.entity.PatternType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Returns a pattern if the node matches the pattern. The argument is the path of the file where
 * the node appears.
 */
public abstract class PatternMatcher extends GenericVisitorWithDefaults<List<PatternInstance>, ClassLocation> {

    protected DataDefinitionFinder ddFinder = new DataDefinitionFinder();

    /**
     * Convenience method that returns a singleton list with the pattern if the condition holds,
     * otherwise an empty list. Exists because most detectors return only one pattern per match.
     *
     * @param node          The node.
     * @param classLocation File where the pattern is found.
     * @param condition     Has to hold for a valid pattern.
     * @param rawOperands   The nodes that correspond to the pattern operands.
     * @return Singleton or empty list.
     */
    protected <T extends Node> List<PatternInstance> makePattern(T node, ClassLocation classLocation,
                                                                 Predicate<T> condition,
                                                                 Node... rawOperands) {
        if (!condition.test(node)) {
            return Collections.emptyList();
        }

        var newPattern = new ASTPattern(
                classLocation.makePatternLocation(node),
                getPatternType(),
                makeOperands(true, rawOperands)
        );

        return Collections.singletonList(new PatternInstance(node, newPattern, rawOperands));
    }

    /**
     * @param fullMethodCalls Whether to extract operand text only from methods scopes/names
     *                        as opposed to the full call (including arguments)
     * @param rawOperands     Operands to process.
     * @return List of operand objects.
     */
    protected List<PatternOperand> makeOperands(boolean fullMethodCalls, Node... rawOperands) {
        return Arrays.stream(rawOperands)
                .map(n -> new PatternOperand(
                        n,
                        n.accept(ddFinder, null)
                                .orElse(null),
                        fullMethodCalls
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<PatternInstance> defaultAction(Node n, ClassLocation arg) {
        return Collections.emptyList();
    }

    @Override
    public List<PatternInstance> defaultAction(NodeList n, ClassLocation arg) {
        return Collections.emptyList();
    }

    public abstract PatternType getPatternType();
}
