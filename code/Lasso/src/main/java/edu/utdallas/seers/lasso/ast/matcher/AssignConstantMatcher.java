package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import edu.utdallas.seers.lasso.ast.ClassLocation;
import edu.utdallas.seers.lasso.data.entity.NameValueASTPattern;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * TODO perhaps we want to allow operations that contain only literals such as 2 * 2
 */
public class AssignConstantMatcher extends PatternMatcher {

    private final Logger logger = LoggerFactory.getLogger(AssignConstantMatcher.class);
    private final ConstantExtractor constantExtractor = new ConstantExtractor();
    private final VariableExtractor variableExtractor = new VariableExtractor();

    @Override
    public List<PatternInstance> visit(VariableDeclarator n, ClassLocation arg) {
        return n.getInitializer()
                .flatMap(e -> constantExtractor.extractConstant(e, false))
                .map(c -> {
                    Attribute attribute;
                    try {
                        attribute = variableExtractor.extractAttribute(n);
                    } catch (Exception e) {
                        // TODO this can fail if the expression is in a method overridden in an enum constant
                        //  avoid this broad catch by solving the issue
//                        logger.error("Could not extract variable ({})",
//                                e.getClass().getName());
                        return null;
                    }

                    var rawOperands = new Node[]{n.getName(), n.getInitializer().get()};
                    var newPattern = new NameValueASTPattern(
                            arg.makePatternLocation(n),
                            getPatternType(),
                            makeOperands(true, rawOperands),
                            c,
                            attribute
                    );

                    return new PatternInstance(n, newPattern, rawOperands);
                })
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public List<PatternInstance> visit(AssignExpr n, ClassLocation arg) {
        return constantExtractor.extractConstant(n.getValue(), true)
                .flatMap(c -> {
                    Optional<Attribute> attribute;
                    try {
                        attribute = variableExtractor.extractForTarget(n.getTarget());
                    } catch (Exception e) {
                        // TODO this can fail if the expression is in a method overridden in an enum constant
//                        logger.error("Could not extract variable: {}", e.getClass().getName());
                        return Optional.empty();
                    }
                    return attribute
                            .map(v -> {
                                var rawOperands = new Node[]{n.getTarget(), n.getValue()};
                                var newPattern = new NameValueASTPattern(
                                        arg.makePatternLocation(n),
                                        getPatternType(),
                                        makeOperands(true, rawOperands),
                                        c,
                                        v
                                );
                                return new PatternInstance(n, newPattern, rawOperands);
                            });
                })
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.ASSIGN_CONSTANT;
    }

    /**
     * Finds the variable for a constant assignment, e.g. fully-qualified field name or variable name +
     * fully qualified method name.
     */
    private static class VariableExtractor extends GenericVisitorWithDefaults<Attribute, String> {

        public Attribute extractAttribute(VariableDeclarator declarator) {
            return extractFromResolved(declarator, declarator.resolve())
                    .orElseThrow(() -> new IllegalStateException("Should find variable"));
        }

        public Optional<Attribute> extractForTarget(Expression target) {
            // Target of an assignment expression

            ResolvedValueDeclaration declaration;
            try {
                // TODO target could be a combination of enclosed expr and field access
                if (target.isNameExpr()) {
                    declaration = target.asNameExpr().resolve();
                } else if (target.isFieldAccessExpr()) {
                    declaration = target.asFieldAccessExpr().resolve();
                    // TODO: local vars?
                } else {
                    return Optional.empty();
                }
            } catch (Exception ignore) {
                // TODO reevaluate this after figuring out resolution from libraries and exclusion of tests (see ASTPatternDetector)
                return Optional.empty();
            }

            return extractFromResolved(target, declaration);
        }

        private Optional<Attribute> extractFromResolved(Node node, ResolvedValueDeclaration declaration) {
            if (declaration.isField()) {
                ResolvedFieldDeclaration fieldDeclaration = declaration.asField();
                ResolvedTypeDeclaration type = fieldDeclaration.declaringType();

                return Optional.of(Attribute.newField(type.getQualifiedName(), fieldDeclaration.getName()));
            } else if (declaration.isParameter() || declaration.isVariable() ||
                    // FIXME JavaParser bug, isVariable should return true in this case
                    (declaration.getClass().equals(JavaParserSymbolDeclaration.class) &&
                            ((JavaParserSymbolDeclaration) declaration).getWrappedNode() instanceof VariableDeclarator)) {
                // We consider these local variables so just check parents as normal
                // We need to check parents because the resolved parameters and variables don't have the method name
                return Optional.of(node.accept(this, declaration.getName()));
            }

            return Optional.empty();
        }

        @Override
        public Attribute visit(MethodDeclaration n, String arg) {
            ResolvedMethodDeclaration declaration = n.resolve();

            return Attribute.newLocal(
                    declaration.declaringType().getQualifiedName(),
                    declaration.getName(),
                    arg
            );
        }

        @Override
        public Attribute visit(ConstructorDeclaration n, String arg) {
            ResolvedConstructorDeclaration declaration = n.resolve();

            return Attribute.newCtorLocal(
                    declaration.declaringType().getQualifiedName(),
                    arg
            );
        }

        @Override
        public Attribute defaultAction(Node n, String arg) {
            return n.getParentNode()
                    // FIXME we are missing initializer blocks
                    .orElseThrow(() -> new IllegalStateException("Should have found suitable parent"))
                    .accept(this, arg);
        }

        @Override
        public Attribute defaultAction(NodeList n, String arg) {
            return super.defaultAction((Node) null, null);
        }
    }
}
