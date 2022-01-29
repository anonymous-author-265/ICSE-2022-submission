package edu.utdallas.seers.lasso.data.entity;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import edu.utdallas.seers.lasso.ast.matcher.ConstantArgumentMatcher;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class PatternOperand {

    public final String text;
    private final DataDefinition dataDefinition;

    public PatternOperand(Node node, DataDefinition dataDefinition, boolean fullMethodCalls) {
        text = extractText(node, fullMethodCalls);
        this.dataDefinition = dataDefinition;
    }

    private String extractText(Node node, boolean fullMethodCalls) {
        if (!fullMethodCalls &&
                ConstantArgumentMatcher.VALID_NODES.contains(node.getClass())) {
            var arguments = ((NodeWithArguments<?>) node).getArguments();
            return node.getChildNodes().stream()
                    // Using == to bypass Node's expensive equals(), since these references will not change
                    .filter(n -> arguments.stream().noneMatch(a -> n == a))
                    .map(Node::toString)
                    .collect(Collectors.joining(" "));
        }

        return node.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternOperand that = (PatternOperand) o;
        return text.equals(that.text) && dataDefinition.equals(that.dataDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, dataDefinition);
    }

    public String getAllText() {
        return text +
                Optional.ofNullable(dataDefinition)
                        .map(dd -> " " + dd.text)
                        .orElse("");
    }

    public Optional<DataDefinition> getDataDefinition() {
        return Optional.ofNullable(dataDefinition);
    }
}
