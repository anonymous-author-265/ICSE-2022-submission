package edu.utdallas.seers.lasso.identifier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class IdentifierExtractor extends GenericVisitorAdapter<Stream<Attribute>, Identifier> {
    /**
     * Used as part of a variable qualifier when it is found inside a class constructor or an
     * initializer block.
     * E.g. my.package.Class.[init].var
     */
    private static final String INIT_QUALIFIER_NAME = "<init>";

    /**
     * Used when the variable is inside a static initializer block inside a class.
     * E.g. my.package.class.[clinit].var
     */
    private static final String STATIC_INIT_QUALIFIER_NAME = "<clinit>";

    private final boolean filter;

    public IdentifierExtractor(boolean filter) {
        this.filter = filter;
    }

    private Stream<Attribute> extractFromType(TypeDeclaration<?> typeDeclaration, Identifier qualifier) {
        String typeName = typeDeclaration.getNameAsString();

        // The qualifier for the members includes the name of the class
        Identifier typeIdentifier = qualifier.addType(typeName);

        // The variable that represents this class
        var type = Attribute.newType(typeIdentifier);

        Predicate<BodyDeclaration<?>> isCallable = BodyDeclaration::isCallableDeclaration;

        // Enum constants won't show up as members
        var enumConstants = typeDeclaration.toEnumDeclaration()
                .map(d -> d.getEntries().stream())
                .orElse(Stream.empty());

        Stream<BodyDeclaration<?>> nonCallableMembers = typeDeclaration.getMembers().stream()
                .filter(isCallable.negate());

        Stream<Attribute> nonCallableMemberVariables = Stream.concat(
                nonCallableMembers,
                enumConstants
        )
                .flatMap(bd -> {
                    if (bd.isTypeDeclaration()) {
                        // TODO: do we wanna extract methods from interfaces?
                        return extractFromType(bd.asTypeDeclaration(), typeIdentifier);
                    } else if (bd.isAnnotationMemberDeclaration()) {
                        // TODO: might want to create a kind for annotation members
                        return Stream.of(Attribute.newField(
                                typeIdentifier,
                                bd.asAnnotationMemberDeclaration().getNameAsString()
                        ));
                    } else if (bd.isEnumConstantDeclaration()) {
                        // TODO: might want to give different treatment to enum constants
                        return Stream.of(Attribute.newField(
                                typeIdentifier,
                                bd.asEnumConstantDeclaration().getNameAsString()
                        ));
                    } else if (bd.isFieldDeclaration()) {
                        return bd.asFieldDeclaration().getVariables().stream()
                                .map(vd -> Attribute.newField(typeIdentifier, vd.getNameAsString()));
                    } else if (bd.isInitializerDeclaration()) {
                        var initializerDeclaration = bd.asInitializerDeclaration();
                        String qualifierName = initializerDeclaration.isStatic() ?
                                STATIC_INIT_QUALIFIER_NAME :
                                INIT_QUALIFIER_NAME;
                        return extractFromBlock(initializerDeclaration.getBody(), typeIdentifier, qualifierName);
                    } else {
                        throw new RuntimeException("Unknown type of body declaration: " + bd.getClass());
                    }
                });

        // Treat all overloads as the same method
        Stream<Attribute> callableMemberVariables = typeDeclaration.getMembers().stream()
                .filter(isCallable)
                .map(d -> ((CallableDeclaration<?>) d.asCallableDeclaration()))
                .flatMap(c -> extractFromCallable(c, typeIdentifier))
                .distinct();

        return Stream.of(Stream.of(type), nonCallableMemberVariables, callableMemberVariables)
                .flatMap(v -> v)
                .filter(v -> (v.getType() != Attribute.Type.LOCAL_VARIABLE && v.getType() != Attribute.Type.METHOD_PARAMETER || !filter));
    }

    private Stream<Attribute> extractFromCallable(CallableDeclaration<?> declaration, Identifier qualifier) {
        String callableName = extractCallableName(declaration);

        // Constructors will appear as methods
        Stream<? extends Attribute> returnVariable = Stream.of(
                Attribute.newMethod(qualifier, callableName)
        );

        Stream<Attribute> parameterVariables = declaration.getParameters().stream()
                .map(p -> Attribute.newMethodParameter(qualifier, callableName, p.getNameAsString()));

        Optional<BlockStmt> body;
        declaration.isConstructorDeclaration();
        if (declaration.isConstructorDeclaration()) {
            body = Optional.of(((ConstructorDeclaration) declaration).getBody());
        } else {
            body = ((MethodDeclaration) declaration).getBody();
        }

        Stream<? extends Attribute> bodyVariables = body
                .map(block -> extractFromBlock(block, qualifier, callableName))
                .orElse(Stream.empty());

        return Stream.of(returnVariable, parameterVariables, bodyVariables)
                .flatMap(v -> v);
    }

    /**
     * Returns the name of the method or CTOR_QUALIFIER_NAME if it's a constructor.
     *
     * @param callable To extract name from.
     * @return Appropriate name.
     */
    private String extractCallableName(CallableDeclaration<?> callable) {
        return callable.isConstructorDeclaration() ? INIT_QUALIFIER_NAME : callable.asMethodDeclaration().getNameAsString();
    }

    private Stream<? extends Attribute> extractFromBlock(BlockStmt block, Identifier qualifier, String methodName) {
        var visitor = new GenericVisitorWithDefaults<Stream<? extends Attribute>, Void>() {
            @Override
            public Stream<? extends Attribute> defaultAction(Node n, Void arg) {
                return Stream.empty();
            }

            @Override
            public Stream<? extends Attribute> visit(VariableDeclarationExpr declaration, Void arg) {
                return declaration.getVariables().stream()
                        .map(v -> Attribute.newLocal(
                                qualifier,
                                methodName,
                                v.getNameAsString()
                        ));
            }
        };

        return block.stream()
                .flatMap(n -> (Stream<? extends Attribute>) n.accept(visitor, null))
                // Multiple contexts might generate duplicate variables
                .distinct();
    }

    public Stream<Attribute> visit(Path file) throws IOException {
        CompilationUnit unit = StaticJavaParser.parse(file);
        String packageName = unit.getPackageDeclaration()
                .map(d -> d.getName().toString())
                .orElse(null);

        return visit(unit, new Identifier(packageName));
    }

    @Override
    public Stream<Attribute> visit(CompilationUnit unit, Identifier qualifier) {
        return unit.getTypes().stream()
                .flatMap(t -> extractFromType(t, qualifier));
    }
}
