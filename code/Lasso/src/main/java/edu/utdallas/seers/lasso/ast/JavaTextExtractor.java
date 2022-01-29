package edu.utdallas.seers.lasso.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithIdentifier;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import org.jooq.lambda.Seq;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

// TODO don't think we are extracting text from literals like 'double' or 'false' or 'null'
public class JavaTextExtractor {

    private final NonCommentVisitor nonCommentVisitor = new NonCommentVisitor();
    private final CommentVisitor commentVisitor = new CommentVisitor();
    private final LocationFinder locationFinder = new LocationFinder();
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(new DummyTypeSolver()))
    );

    public Stream<TextSpan> extractText(Path file) throws IOException {
        return extractText(parser.parse(file).getResult().orElseThrow());
    }

    public Stream<TextSpan> extractFromNode(Node node) {
        var javaPath = extractJavaPath(node.findCompilationUnit().orElseThrow());
        Predicate<Node> isComment = n -> n instanceof Comment;

        return Stream.concat(
                node.stream()
                        .filter(isComment.negate())
                        .flatMap(n -> n.accept(nonCommentVisitor, javaPath).stream()),
                node.stream()
                        .filter(isComment)
                        // TODO see that it is actually traversing comments
                        .distinct()
                        .flatMap(n -> n.accept(commentVisitor, javaPath).stream())
        );
    }

    public Stream<TextSpan> extractText(CompilationUnit compilationUnit) {
        String javaPath = extractJavaPath(compilationUnit);
        // Must visit non-comments and then comments because comments appear inconsistently in the stream
        return Stream.concat(
                // Skip package and import declarations
                compilationUnit.getTypes().stream()
                        .flatMap(Node::stream)
                        .flatMap(n -> n.accept(nonCommentVisitor, javaPath).stream()),
                compilationUnit.getAllComments().stream()
                        // Comments can appear duplicated sometimes
                        // Shouldn't use distinct on a stream of arbitrary nodes because the comparison is very expensive
                        .distinct()
                        .flatMap(n -> n.accept(commentVisitor, javaPath).stream()));
    }

    private String extractJavaPath(CompilationUnit compilationUnit) {
        // getPrimaryTypeName doesn't work if the CU is empty
        return compilationUnit.getPackageDeclaration()
                .map(d -> d.getName().asString().replace(".", "/") + "/")
                .orElse("") + compilationUnit.getPrimaryTypeName().orElseThrow() + ".java";
    }

    private class NonCommentVisitor extends GenericVisitorWithDefaults<List<TextSpan>, String> {
        private <T extends Node & NodeWithIdentifier<?>> List<TextSpan> extractIdentifier(T node, String packagePath) {
            var location = locationFinder.findLocation(node, packagePath);
            return Collections.singletonList(
                    new TextSpan(TextSpan.Type.IDENTIFIER, node, location, node.getIdentifier())
            );
        }

        private List<TextSpan> extractNumber(LiteralStringValueExpr node, String packagePath) {
            var location = locationFinder.findLocation(node, packagePath);
            return Collections.singletonList(new TextSpan(TextSpan.Type.NUMBER, node, location, node.getValue()));
        }

        @Override
        public List<TextSpan> visit(CharLiteralExpr n, String arg) {
            // TODO char literals may not have enough text to matter, but we may use them in the future
            return Collections.emptyList();
        }

        @Override
        public List<TextSpan> visit(DoubleLiteralExpr n, String arg) {
            return extractNumber(n, arg);
        }

        @Override
        public List<TextSpan> visit(IntegerLiteralExpr n, String arg) {
            return extractNumber(n, arg);
        }

        @Override
        public List<TextSpan> visit(LongLiteralExpr n, String arg) {
            return extractNumber(n, arg);
        }

        @Override
        public List<TextSpan> visit(StringLiteralExpr n, String arg) {
            var location = locationFinder.findLocation(n, arg);
            return Collections.singletonList(new TextSpan(TextSpan.Type.STRING, n, location, n.getValue()));
        }

        @Override
        public List<TextSpan> visit(TextBlockLiteralExpr n, String arg) {
            return super.visit(n, arg);
        }

        // TODO: make sure it extracts from type arguments
        @Override
        public List<TextSpan> visit(SimpleName n, String arg) {
            return extractIdentifier(n, arg);
        }

        @Override
        public List<TextSpan> visit(Name n, String arg) {
            // TODO: test
            // The rest of the name will be visited eventually
            return extractIdentifier(n, arg);
        }

        @Override
        public List<TextSpan> visit(MethodReferenceExpr n, String arg) {
            return extractIdentifier(n, arg);
        }

        @Override
        public List<TextSpan> defaultAction(Node n, String arg) {
            return Collections.emptyList();
        }

        @Override
        public List<TextSpan> defaultAction(NodeList n, String arg) {
            return Collections.emptyList();
        }
    }

    private class CommentVisitor extends GenericVisitorWithDefaults<List<TextSpan>, String> {

        private List<TextSpan> splitComment(Comment comment, String packagePath) {
            var range = comment.getRange().orElseThrow();
            var split = comment.getContent().split("\\n", -1);
            assert (range.end.line - range.begin.line) + 1 == split.length;
            var location = locationFinder.findLocation(comment, packagePath);
            var docComment = isDocComment(comment, location);

            return Seq.zip(Arrays.stream(split), Seq.rangeClosed(range.begin.line, range.end.line))
                    .map(t -> TextSpan.createCommentSpan(location, t.v2, t.v1, docComment))
                    .toList();
        }

        private boolean isDocComment(Comment comment, TextSpan.Location location) {
            return comment.getCommentedNode()
                    .filter(n -> n instanceof MethodDeclaration)
                    .flatMap(n -> location.getMethodName()
                            .map(s -> ((MethodDeclaration) n).getName().asString().equals(s)))
                    .orElse(false);
        }

        // TODO test that all comments are traversed
        @Override
        public List<TextSpan> visit(JavadocComment n, String arg) {
            return splitComment(n, arg);
        }

        @Override
        public List<TextSpan> visit(BlockComment n, String arg) {
            return splitComment(n, arg);
        }

        @Override
        public List<TextSpan> visit(LineComment n, String arg) {
            var location = locationFinder.findLocation(n, arg);
            var isDocComment = isDocComment(n, location);
            int line = n.getRange().orElseThrow().begin.line;
            return Collections.singletonList(TextSpan.createCommentSpan(location, line, n.getContent(), isDocComment));
        }

        @Override
        public List<TextSpan> defaultAction(Node n, String arg) {
            return Collections.emptyList();
        }

        @Override
        public List<TextSpan> defaultAction(NodeList n, String arg) {
            return Collections.emptyList();
        }
    }
}
