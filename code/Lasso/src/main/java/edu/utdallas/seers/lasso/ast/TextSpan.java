package edu.utdallas.seers.lasso.ast;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import edu.utdallas.seers.retrieval.Retrievable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TextSpan implements Retrievable {

    private final Type type;
    private final String text;
    private final int line;
    private final Location location;
    public final boolean docComment;

    TextSpan(Type type, Node node, Location location, String text) {
        assert !text.contains("\n");
        this.type = type;
        this.location = location;
        this.text = text;

        var range = node.getRange().orElseThrow();
        assert range.begin.line == range.end.line;
        line = range.begin.line;
        docComment = false;
    }

    public TextSpan(Type type, Location location, int line) {
        this.type = type;
        this.location = location;
        text = null;
        this.line = line;
        docComment = false;
    }

    private TextSpan(Location location, int line, String text, boolean docComment) {
        assert !text.contains("\n");
        this.location = location;
        type = Type.COMMENT;
        this.text = text;
        this.line = line;
        this.docComment = docComment;
    }

    static TextSpan createCommentSpan(Location location, int line, String text, boolean docComment) {
        // Exists to avoid misuse of this constructor which should only be used for comments
        return new TextSpan(location, line, text, docComment);
    }

    public static TextSpan createBrief(String type, String locString, int line) {
        return new TextSpan(Type.valueOf(type), Location.fromString(locString), line);
    }

    @Override
    public String getID() {
        // FIXME this class has no short natural ID
        return null;
    }

    public Location getLocation() {
        return location;
    }

    public Type getType() {
        return type;
    }

    public int getLine() {
        return line;
    }

    public String getText() {
        return text;
    }

    public enum Type {
        IDENTIFIER,
        NUMBER,
        STRING,
        COMMENT;

        public static String shorten(Set<Type> sources) {
            return sources.stream()
                    .map(s -> s.toString().substring(0, 1))
                    .sorted()
                    .collect(Collectors.joining(""));
        }
    }

    // TODO merge all location classes
    public static class Location {

        public static final String SEPARATOR = ";";

        private final String file;
        private final String className;
        private final String methodName;
        public final Range methodRange;
        public final Range statementRange;

        public Location(String file, String className, String methodName,
                        Range methodRange, Range statementRange) {
            this.file = Objects.requireNonNull(file);
            this.className = className;
            this.methodName = methodName;
            this.methodRange = methodRange;
            this.statementRange = statementRange;
        }

        public static Location fromString(String string) {
            var comps = Arrays.stream(string.split(SEPARATOR, -1))
                    .map(s -> s.isEmpty() ? null : s)
                    .collect(Collectors.toList());

            return new Location(comps.get(0), comps.get(1), comps.get(2), null, null);
        }

        // FIXME these equals and hashcode do not consider method/statement range, which works for what we need them to do
        //  In the future, extract these data to different classes or handle uniformly
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Location location = (Location) o;
            return file.equals(location.file) && Objects.equals(className, location.className) && Objects.equals(methodName, location.methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, className, methodName);
        }

        @Override
        public String toString() {
            return Stream.of(file, className, methodName)
                    .map(s -> s == null ? "" : s)
                    .collect(Collectors.joining(SEPARATOR));
        }

        public Optional<String> getMethodName() {
            return Optional.ofNullable(methodName);
        }

        public String getFile() {
            return file;
        }

        public Optional<String> getClassName() {
            return Optional.ofNullable(className);
        }
    }
}
