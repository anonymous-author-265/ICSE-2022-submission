package edu.utdallas.seers.lasso.data.entity;

import com.github.javaparser.Range;
import edu.utdallas.seers.lasso.ast.PatternStore;
import edu.utdallas.seers.retrieval.Retrievable;
import org.jooq.lambda.Seq;

import java.nio.file.Path;
import java.util.*;

public class ASTPattern implements Retrievable {
    public final Location location;
    private final PatternType patternType;
    private final String fileName;
    private final Set<Integer> lines;
    private final List<PatternOperand> operands;
    private final Path filePath;

    public ASTPattern(Location location, PatternType patternType, List<PatternOperand> operands) {
        this.patternType = patternType;
        this.operands = operands;
        this.fileName = location.packagePath;
        this.lines = location.getLineNumbers();
        this.filePath = location.filePath;
        this.location = location;
    }

    public void accept(PatternStore store) {
        store.addPattern(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ASTPattern that = (ASTPattern) o;
        return patternType == that.patternType && fileName.equals(that.fileName) && lines.equals(that.lines) && operands.equals(that.operands) && filePath.equals(that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patternType, fileName, lines, operands, filePath);
    }

    @Override
    public String getID() {
        return String.join(";", Arrays.asList(
                patternType.toString(),
                location.toString()
        ));
    }

    @Override
    public String toString() {
        return getID();
    }

    public List<PatternOperand> getOperands() {
        return operands;
    }

    public Path getFilePath() {
        return filePath;
    }

    @Deprecated
    public Set<Integer> getLines() {
        return lines;
    }

    public String getFileName() {
        return fileName;
    }

    public PatternType getPatternType() {
        return patternType;
    }

    public static class Location {

        public final String packagePath;
        public final Path filePath;
        public final Range range;
        // TODO: encapsulate these three as "method reference"
        public final Range methodRange;
        private final String className;
        private final String methodName;

        public Location(Path filePath, String packagePath, Range range, Range methodRange, String className, String methodName) {
            this.filePath = filePath;
            this.packagePath = packagePath;
            this.range = range;
            this.methodRange = methodRange;
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public String toString() {
            return String.format("%s:%d,%d-%d,%d",
                    packagePath,
                    range.begin.line,
                    range.begin.column,
                    range.end.line,
                    range.end.column);
        }

        public Optional<String> getClassName() {
            return Optional.ofNullable(className);
        }

        public Optional<String> getMethodName() {
            return Optional.ofNullable(methodName);
        }

        public Set<Integer> getLineNumbers() {
            return Seq.rangeClosed(range.begin.line, range.end.line)
                    .toSet();
        }
    }
}
