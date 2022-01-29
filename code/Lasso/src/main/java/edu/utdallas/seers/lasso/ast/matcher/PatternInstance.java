package edu.utdallas.seers.lasso.ast.matcher;

import com.github.javaparser.ast.Node;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;

import java.util.Arrays;
import java.util.List;

/**
 * Contains the information of a successful pattern match.
 * <p>
 * NOTE: Since the Node instances keep references to the whole AST, do not store references to this
 * object, instead process the nodes as needed and then keep the {@link PatternInstance#match} object.
 */
public class PatternInstance {

    public final Node matchedNode;
    public final List<Node> operandNodes;
    public final ASTPattern match;

    PatternInstance(Node matchedNode, ASTPattern match, Node... operandNodes) {
        this.matchedNode = matchedNode;
        this.operandNodes = Arrays.asList(operandNodes);
        this.match = match;
    }
}
