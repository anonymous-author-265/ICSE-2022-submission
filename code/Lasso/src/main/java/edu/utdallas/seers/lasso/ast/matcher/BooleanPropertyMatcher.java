package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.google.common.collect.ImmutableSet;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

// FIXME: The guess functionality is accepting too many bogus results
public class BooleanPropertyMatcher extends PatternMatcher {
    private final Logger logger = LoggerFactory.getLogger(BooleanPropertyMatcher.class);
    private final Set<BinaryExpr.Operator> booleanOperators = ImmutableSet.of(
            BinaryExpr.Operator.OR, BinaryExpr.Operator.AND, BinaryExpr.Operator.XOR,
            BinaryExpr.Operator.EQUALS, BinaryExpr.Operator.NOT_EQUALS
    );
    private final BooleanExpressionGuesser guesser = new BooleanExpressionGuesser();

    private <T, E extends Expression & Resolvable<T>>
    List<PatternInstance> matchNode(E node, ClassLocation location, Function<T, ResolvedType> typeGetter) {
        if (!isValid(node)) {
            return Collections.emptyList();
        }

        Optional<T> resolvedNode;
        if ((node.getRange().map(r -> r.begin.line == 354).orElse(false) &&
                node.toString().contains("getRawType")) ||
                (node.getRange().map(r -> r.begin.line == 243).orElse(false) &&
                        node.toString().contains("nextSibling.previousSibling"))) {
            // FIXME see if there is any other way to prevent Stack Overflow
            return Collections.emptyList();
        }
        try {
            resolvedNode = Optional.of(node.resolve())
                    .filter(n -> isBoolean(typeGetter.apply(n)));
        } catch (UnsolvedSymbolException ignore) {
            resolvedNode = Optional.empty();
        } catch (Exception e) {
            // Resolution is not possible if there is no compilation unit. Allow this case for testing
            Optional<CompilationUnit> unit = node.findCompilationUnit();
            if (unit.isPresent()) {
                // If the unit is present the error must be due to something else, so report it
                // TODO Very nasty errors having to do with resolution, see if we can at least see them coming
//                logger.warn("Type resolution error at line {} of {}",
//                        node.getRange().map(r -> String.valueOf(r.begin.line)).orElse("<no line>"),
//                        unit.flatMap(CompilationUnit::getStorage).map(s -> s.getPath().toString()).orElse("<no file>")
//                );
            }
            resolvedNode = Optional.empty();
        }

        return resolvedNode
                .map(n -> makePattern(node, location, nn -> true, node))
                .orElse(Collections.emptyList());
    }

    private boolean isBoolean(ResolvedType type) {
        if (type.isReferenceType()) {
            var refType = type.asReferenceType();
            // Due to boxing it'd say that any object is assignable by Boolean
            return !refType.getQualifiedName().equals(Object.class.getCanonicalName()) &&
                    refType.isAssignableBy(ResolvedPrimitiveType.BOOLEAN);
        }

        return type.equals(ResolvedPrimitiveType.BOOLEAN);
    }

    private boolean isValid(Node node) {
        var parent = node.getParentNode().orElseThrow();

        if (parent instanceof EnclosedExpr) {
            return isValid(parent);
        }

        if (parent instanceof ExpressionStmt) {
            return false;
        } else if (parent instanceof AssignExpr) {
            return ((AssignExpr) parent).getTarget() != node;
        } else if (parent instanceof ConditionalExpr) {
            return ((ConditionalExpr) parent).getCondition() == node;
        } else if (parent instanceof BinaryExpr) {
            return booleanOperators.contains(((BinaryExpr) parent).getOperator());
        } else {
            return (parent instanceof UnaryExpr && ((UnaryExpr) parent).getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) ||
                    !(parent instanceof Expression);
        }
    }

    @Override
    public List<PatternInstance> visit(FieldAccessExpr n, ClassLocation arg) {
        return matchNode(n, arg, ResolvedValueDeclaration::getType);
    }

    @Override
    public List<PatternInstance> visit(MethodCallExpr n, ClassLocation arg) {
        return matchNode(n, arg, ResolvedMethodDeclaration::getReturnType);
    }

    @Override
    public List<PatternInstance> visit(NameExpr n, ClassLocation arg) {
        if (n.getParentNode()
                .map(p -> p instanceof FieldAccessExpr)
                .orElse(false)) {
            return Collections.emptyList();
        }

        return matchNode(n, arg, ResolvedValueDeclaration::getType);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.BOOLEAN_PROPERTY;
    }
}
