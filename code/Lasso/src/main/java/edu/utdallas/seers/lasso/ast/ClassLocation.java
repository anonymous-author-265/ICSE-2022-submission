package edu.utdallas.seers.lasso.ast;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;

import java.nio.file.Path;
import java.util.Optional;

public class ClassLocation {

    public final Path filePath;
    public final String packagePath;
    private final LocationFinder locationFinder = new LocationFinder();

    public ClassLocation(Path filePath, String packagePath) {
        this.filePath = filePath;
        this.packagePath = packagePath;
    }

    private Range findMethodRange(TextSpan.Location location) {
        return Optional.ofNullable(location.methodRange)
                // Exception if null
                .or(() -> Optional.of(location.statementRange))
                .get();
    }

    public ASTPattern.Location makePatternLocation(Node node) {
        var location = locationFinder.findLocation(node, packagePath);
        return new ASTPattern.Location(filePath, packagePath, node.getRange().orElseThrow(),
                findMethodRange(location), location.getClassName().orElse(null),
                location.getMethodName().orElse(null));
    }

    public ASTPattern.Location makePatternLocation(Node firstNode, Node secondNode) {
        var range1 = firstNode.getRange().orElseThrow();
        var range2 = secondNode.getRange().orElseThrow();

        Range newRange = new Range(
                new Position(range1.begin.line, range1.begin.column),
                new Position(range2.end.line, range2.end.column)
        );

        var location = locationFinder.findLocation(firstNode, packagePath);
        return new ASTPattern.Location(filePath, packagePath, newRange, findMethodRange(location),
                location.getClassName().orElse(null),
                location.getMethodName().orElse(null));
    }
}
