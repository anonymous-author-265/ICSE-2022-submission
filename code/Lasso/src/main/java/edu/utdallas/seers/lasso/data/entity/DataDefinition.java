package edu.utdallas.seers.lasso.data.entity;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

public class DataDefinition {

    public final Path file;
    public final Range range;
    public final String text;

    public DataDefinition(Node node) {
        file = node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(CompilationUnit.Storage::getPath)
                .orElseThrow();
        range = node.getRange().orElseThrow();
        text = extractText(node);
    }

    // TODO: include comments?
    public DataDefinition(CallableDeclaration<?> node) {
        file = node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(CompilationUnit.Storage::getPath)
                .orElseThrow();
        range = node.getRange().orElseThrow();
        text = extractText(node);
    }

    private String extractText(Node node) {
        var children = node.getChildNodes();
        return children.stream()
                .filter(n -> !(n instanceof BlockStmt) &&
                        // Enum constants can have body declarations
                        !(n instanceof BodyDeclaration) &&
                        !(n instanceof Modifier))
                .map(Node::toString)
                .collect(Collectors.joining(" "));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataDefinition that = (DataDefinition) o;
        return file.equals(that.file) && range.equals(that.range);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, range);
    }
}
