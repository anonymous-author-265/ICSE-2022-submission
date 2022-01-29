package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.declarations.ResolvedDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.*;
import com.google.common.collect.ImmutableMap;
import edu.utdallas.seers.collection.Collections;
import edu.utdallas.seers.lasso.data.entity.DataDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// TODO might need to implement more thorough resolution, e.g. enclosed expressions and accounting for other subclasses of resolved symbols
public class DataDefinitionFinder
        extends GenericVisitorWithDefaults<Optional<DataDefinition>, Void> {

    private final Logger logger = LoggerFactory.getLogger(DataDefinitionFinder.class);
    private final Map<Class<? extends ResolvedDeclaration>, Function<ResolvedDeclaration, Node>> valueDeclarationStrategies = ImmutableMap.of(
            JavaParserEnumConstantDeclaration.class, d -> ((JavaParserEnumConstantDeclaration) d).getWrappedNode(),
            JavaParserFieldDeclaration.class, d -> ((JavaParserFieldDeclaration) d).getWrappedNode(),
            JavaParserParameterDeclaration.class, d -> ((JavaParserParameterDeclaration) d).getWrappedNode(),
            JavaParserSymbolDeclaration.class, d -> ((JavaParserSymbolDeclaration) d).getWrappedNode(),
            JavaParserVariableDeclaration.class, d -> ((JavaParserVariableDeclaration) d).getWrappedNode()
    );

    private <T extends Node & Resolvable<R>, U extends ResolvedDeclaration, R>
    Optional<DataDefinition> resolveDeclaration(T node, Class<U> targetResolutionClass, Function<U, Node> convert, String name) {
        R resolved;
        try {
            resolved = node.resolve();
        } catch (RuntimeException e) {
            // FIXME must fix these errors
//            logger.error("Cannot resolve symbol for data definition lookup in {}:{}",
//                    node.findCompilationUnit().flatMap(CompilationUnit::getStorage)
//                            .map(s -> s.getPath().toString()).orElse("<no file>"),
//                    node.getRange().map(r -> r.begin.line + "-" + r.end.line).orElse("<no lines>"));
            return Optional.empty();
        }

        var extractedNode = Optional.of(resolved)
                .filter(targetResolutionClass::isInstance)
                .map(targetResolutionClass::cast)
                .map(convert);

        if (extractedNode.filter(n -> n instanceof CallableDeclaration).isPresent()) {
            return extractedNode.map(n -> new DataDefinition((CallableDeclaration<?>) n));
        }

        // We do this because the resolution pulls the whole declaration, not the individual declarator
        if (extractedNode.filter(n -> n instanceof FieldDeclaration).isPresent()) {
            var declarator = ((FieldDeclaration) extractedNode.get()).getVariables().stream()
                    .filter(d -> d.getName().asString().equals(name))
                    .findFirst()
                    .orElseThrow();

            return Optional.of(new DataDefinition(declarator));
        }

        return extractedNode.map(DataDefinition::new);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node & Resolvable<ResolvedValueDeclaration> & NodeWithSimpleName<?>>
    Optional<DataDefinition> resolveDataDeclaration(T node) {
        var name = node.getNameAsString();
        // TODO this is calling resolve more than once
        return Collections.streamMap(valueDeclarationStrategies)
                .combine((c, f) -> resolveDeclaration(node, ((Class<ResolvedDeclaration>) c), f, name))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public Optional<DataDefinition> visit(MethodCallExpr n, Void arg) {
        return resolveDeclaration(
                n,
                JavaParserMethodDeclaration.class,
                JavaParserMethodDeclaration::getWrappedNode,
                null);
    }

    @Override
    public Optional<DataDefinition> visit(NameExpr n, Void arg) {
        return resolveDataDeclaration(n);
    }

    @Override
    public Optional<DataDefinition> visit(ObjectCreationExpr n, Void arg) {
        // TODO what to do about default constructor declaration?
        return resolveDeclaration(
                n,
                JavaParserConstructorDeclaration.class,
                JavaParserConstructorDeclaration::getWrappedNode,
                null);
    }

    @Override
    public Optional<DataDefinition> visit(FieldAccessExpr n, Void arg) {
        return resolveDataDeclaration(n);
    }

    @Override
    public Optional<DataDefinition> defaultAction(Node n, Void arg) {
        return Optional.empty();
    }
}
