package edu.utdallas.seers.lasso.ast;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithCondition;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import org.jooq.lambda.Seq;

import java.util.*;
import java.util.function.Supplier;

// TODO: instead of finding location by going up, continuously build up location while descending. Use this for ASTPattern location as well
public class LocationFinder extends GenericVisitorWithDefaults<TextSpan.Location, LocationFinder.LocationRecord> {

    public static final String INITIALIZER_TAG = "[init]";
    public static final String STATIC_INITIALIZER_TAG = "[static init]";
    public static final String CONSTRUCTOR_TAG = "[ctor]";

    public TextSpan.Location findLocation(Node node, String packagePath) {
        return node.accept(this, LocationRecord.create(packagePath));
    }

    private TextSpan.Location visitComment(Comment node, LocationRecord location) {
        /* Comments don't register the annotated node as their parent,
         * so we must select it with the commented node method */
        return node.getParentNode().or(node::getCommentedNode).orElseThrow()
                .accept(this, location);
    }

    // FIXME When resolving a type, the qualified name of classes including an anonymous class contains a UUID which probably isn't useful
    private TextSpan.Location visitType(TypeDeclaration<?> n, LocationRecord location) {
        return location.addClassName(n.resolve().getQualifiedName())
                .toLocation();
    }

    private Optional<Node> findProblemNode(Node node) {
        // Temporary hack, we look for methods inside of anonymous classes or Enum Constants which
        // cause problems with type resolution
        if (node instanceof ObjectCreationExpr || node instanceof EnumConstantDeclaration)
            return Optional.of(node);

        return node.getParentNode().flatMap(this::findProblemNode);
    }

    private TextSpan.Location skipAnonymous(Node node, LocationRecord location, Supplier<TextSpan.Location> elseAction) {
        /* FIXME resolution of methods in anonymous classes or enum constants does not work
            without resolution for all project classes.
             This method should be completely removed after that is resolved */
        return findProblemNode(node)
                // Need to remove statement for it to be eventually set to the whole definition
                .map(pn -> pn.accept(this, location.removeStatement()))
                .orElseGet(elseAction);
    }

    private LocationRecord updateLocationStatement(Node node, LocationRecord location) {
        /* Non-functional statements like parameter definitions and comments should be getting
         * no statement range */

        if (location.statementRange != null) {
            return location;
        }

        // Can't accept all BodyDeclarations, e.g., types
        var isStatementBodyDeclaration = node instanceof FieldDeclaration ||
                node instanceof EnumConstantDeclaration ||
                node instanceof AnnotationMemberDeclaration;

        var isSimpleStatement = node instanceof Statement &&
                !(node instanceof BlockStmt ||
                        node instanceof LocalClassDeclarationStmt);

        if (!(isSimpleStatement || isStatementBodyDeclaration)) {
            return location;
        }

        // Has to be a simple statement or a "body statement" to make it here

        // DO, IF, WHILE
        if (node instanceof NodeWithCondition<?>) {
            var range = ((NodeWithCondition<?>) node).getCondition().getRange().orElseThrow();
            return location.addStatement(range.begin, range.end);
        }

        var range = node.getRange().orElseThrow();
        return location.addStatement(range.begin, range.end);
    }

    private TextSpan.Location updateLocationStatementList(Node node, LocationRecord location,
                                                          Supplier<List<Node>> nodesForRange) {
        if (location.statementRange != null) {
            return defaultAction(node, location);
        }

        var nodes = Seq.seq(nodesForRange.get())
                // Only comparing begin is fine because they cannot overlap
                .sorted(Comparator.comparing(e -> e.getBegin().orElseThrow()))
                .toList();

        if (nodes.isEmpty()) {
            return defaultAction(node, location);
        }

        return defaultAction(node, location.addStatement(
                nodes.get(0).getBegin().orElseThrow(),
                nodes.get(nodes.size() - 1).getEnd().orElseThrow()
        ));
    }

    private TextSpan.Location updateLocationStatement(Node node, LocationRecord location,
                                                      Supplier<Node> nodeForRange) {
        return updateLocationStatementList(node, location, () -> Collections.singletonList(nodeForRange.get()));
    }

    // INITIALIZERS AND CALLABLES

    @Override
    public TextSpan.Location visit(MethodDeclaration n, LocationRecord arg) {
        return skipAnonymous(n, arg, () -> {
            var method = n.resolve();
            return arg.addMethod(method.getName(), method.declaringType().getQualifiedName(), n.getRange().orElseThrow())
                    .toLocation();
        });
    }

    @Override
    public TextSpan.Location visit(InitializerDeclaration n, LocationRecord arg) {
        return skipAnonymous(n, arg, () -> {
            var className = n.getParentNode()
                    .map(pn -> ((TypeDeclaration<?>) pn).resolve().getQualifiedName())
                    .orElseThrow();
            var name = n.isStatic() ? STATIC_INITIALIZER_TAG : INITIALIZER_TAG;
            return arg.addMethod(name, className, n.getRange().orElseThrow())
                    .toLocation();
        });
    }

    @Override
    public TextSpan.Location visit(ConstructorDeclaration n, LocationRecord arg) {
        // Cannot define constructors in anonymous classes or enum constants, no need to find problem node
        var constructor = n.resolve();
        return arg.addMethod(CONSTRUCTOR_TAG, constructor.declaringType().getQualifiedName(), n.getRange().orElseThrow())
                .toLocation();
    }

    // TYPES

    @Override
    public TextSpan.Location visit(ClassOrInterfaceDeclaration n, LocationRecord arg) {
        return visitType(n, arg);
    }

    @Override
    public TextSpan.Location visit(EnumDeclaration n, LocationRecord arg) {
        return visitType(n, arg);
    }

    @Override
    public TextSpan.Location visit(AnnotationDeclaration n, LocationRecord arg) {
        return visitType(n, arg);
    }

    // COMMENTS

    @Override
    public TextSpan.Location visit(CompilationUnit n, LocationRecord arg) {
        return arg.toLocation();
    }

    @Override
    public TextSpan.Location visit(JavadocComment n, LocationRecord arg) {
        return visitComment(n, arg);
    }

    @Override
    public TextSpan.Location visit(BlockComment n, LocationRecord arg) {
        return visitComment(n, arg);
    }

    @Override
    public TextSpan.Location visit(LineComment n, LocationRecord arg) {
        return visitComment(n, arg);
    }

    // NODES WITH EXPRESSION HEADERS

    @Override
    public TextSpan.Location visit(SwitchEntry n, LocationRecord arg) {
        return updateLocationStatementList(n, arg,
                // FIXME we don't know which label the expression came from, but only applies to very new code
                () -> new ArrayList<>(n.getLabels())
        );
    }

    @Override
    public TextSpan.Location visit(ForEachStmt n, LocationRecord arg) {
        return updateLocationStatement(n, arg, n::getIterable);
    }

    @Override
    public TextSpan.Location visit(ForStmt n, LocationRecord arg) {
        // FIXME we don't know which expression the text comes from
        return updateLocationStatementList(n, arg,
                () -> Seq.<Node>concat(
                        n.getInitialization().stream(),
                        n.getCompare().stream(),
                        n.getUpdate().stream()
                )
                        .toList()
        );
    }

    @Override
    public TextSpan.Location visit(TryStmt n, LocationRecord arg) {
        return updateLocationStatementList(n, arg, () -> new ArrayList<>(n.getResources()));
    }

    @Override
    public TextSpan.Location visit(SynchronizedStmt n, LocationRecord arg) {
        return updateLocationStatement(n, arg, n::getExpression);
    }

    @Override
    public TextSpan.Location visit(SwitchExpr n, LocationRecord arg) {
        return updateLocationStatement(n, arg, n::getSelector);
    }

    @Override
    public TextSpan.Location visit(LabeledStmt n, LocationRecord arg) {
        return updateLocationStatement(n, arg, n::getLabel);
    }

    // DEFAULTS

    @Override
    public TextSpan.Location defaultAction(Node n, LocationRecord arg) {
        var updated = updateLocationStatement(n, arg);
        return n.getParentNode().orElseThrow().accept(this, updated);
    }

    @SuppressWarnings("unchecked")
    @Override
    public TextSpan.Location defaultAction(NodeList n, LocationRecord arg) {
        return ((Optional<Node>) n.getParentNode()).orElseThrow().accept(this, arg);
    }

    static class LocationRecord {

        private final String filePackagePath;
        private final String className;
        private final String methodName;
        private final Range methodRange;
        private final Range statementRange;

        private LocationRecord(String filePackagePath, String className, String methodName,
                               Range methodRange, Range statementRange) {
            this.filePackagePath = filePackagePath;
            this.className = className;
            this.methodName = methodName;
            this.methodRange = methodRange;
            this.statementRange = statementRange;
        }

        public static LocationRecord create(String filePackagePath) {
            return new LocationRecord(filePackagePath, null, null, null, null);
        }

        public LocationRecord addClassName(String qualifiedName) {
            assert className == null;
            return new LocationRecord(filePackagePath, qualifiedName, methodName, methodRange, statementRange);
        }

        public TextSpan.Location toLocation() {
            return new TextSpan.Location(filePackagePath, className, methodName, methodRange, statementRange);
        }

        public LocationRecord addMethod(String methodName, String className, Range range) {
            assert methodRange == null;
            var methodRange = new Range(range.begin, range.end);
            return new LocationRecord(filePackagePath, className, methodName, methodRange, statementRange);
        }

        public LocationRecord addStatement(Position begin, Position end) {
            return new LocationRecord(filePackagePath, className, methodName, methodRange,
                    new Range(begin, end));
        }

        public LocationRecord removeStatement() {
            if (statementRange == null) {
                return this;
            }

            return new LocationRecord(filePackagePath, className, methodName, methodRange, null);
        }
    }
}
