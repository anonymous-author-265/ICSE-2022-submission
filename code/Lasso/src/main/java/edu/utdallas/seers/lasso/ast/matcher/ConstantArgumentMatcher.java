package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.Resolvable;
import com.google.common.collect.ImmutableSet;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.NameValueASTPattern;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.lambda.tuple.Tuple.tuple;

public class ConstantArgumentMatcher extends PatternMatcher {

    /**
     * Signals which nodes are valid for this pattern for operand text extraction (see usage).
     */
    public static final Set<Class<? extends Node>> VALID_NODES = ImmutableSet.of(
            ObjectCreationExpr.class, MethodCallExpr.class, ExplicitConstructorInvocationStmt.class
    );

    private final ConstantExtractor constantExtractor = new ConstantExtractor();

    private <T extends Node & NodeWithArguments<?> & Resolvable<U>, U>
    List<PatternInstance> extractInstances(T callable, ClassLocation loc, Function<U, Attribute> variableExtractor) {
        var constants = callable.getArguments().stream()
                .flatMap(e -> constantExtractor.extractConstant(e, false)
                        .map(c -> tuple(c, e))
                        .stream()
                )
                .distinct()
                .collect(Collectors.toList());

        if (constants.isEmpty()) {
            return Collections.emptyList();
        }

        Attribute method;
        try {
            method = variableExtractor.apply(callable.resolve());
        } catch (Exception ignore) {
            // TODO reevaluate this after figuring out resolution from libraries and exclusion of tests (see ASTPatternDetector)
            return Collections.emptyList();
        }

        return constants.stream()
                .map(t -> {
                    var rawOperands = new Node[]{callable, t.v2};
                    var newPattern = new NameValueASTPattern(
                            loc.makePatternLocation(callable, t.v2),
                            PatternType.CONSTANT_ARGUMENT,
                            makeOperands(false, rawOperands),
                            t.v1,
                            method
                    );

                    return new PatternInstance(callable, newPattern, rawOperands);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PatternInstance> visit(ObjectCreationExpr n, ClassLocation arg) {
        return extractInstances(n, arg,
                r -> Attribute.newCtor(r.declaringType().getQualifiedName())
        );
    }

    @Override
    public List<PatternInstance> visit(MethodCallExpr n, ClassLocation arg) {
        return extractInstances(n, arg,
                r -> Attribute.newMethod(r.declaringType().getQualifiedName(), r.getName())
        );
    }

    @Override
    public List<PatternInstance> visit(EnumConstantDeclaration n, ClassLocation arg) {
        // TODO
        return super.visit(n, arg);
    }

    @Override
    public List<PatternInstance> visit(ExplicitConstructorInvocationStmt n, ClassLocation arg) {
        return extractInstances(n, arg,
                r -> Attribute.newCtor(r.declaringType().getQualifiedName())
        );
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.CONSTANT_ARGUMENT;
    }
}
