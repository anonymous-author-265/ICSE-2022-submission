package edu.utdallas.seers.lasso.retrieval;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import edu.utdallas.seers.lasso.ast.LocationFinder;
import gr.gousiosg.javacg.stat.JCallGraph;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jooq.lambda.tuple.Tuple.tuple;

public class LassoCallGraphBuilder {

    private final Logger logger = LoggerFactory.getLogger(LassoCallGraphBuilder.class);
    private final Pattern lineParser = Pattern.compile("M:([^(]+\\([^)]*\\))\\s\\([A-Z]\\)([^(]+\\([^)]*\\))");
    private final Pattern parameterFinder = Pattern.compile("\\([^)]*\\)");

    private final Path binariesDirectory;

    public LassoCallGraphBuilder(Path binariesDirectory) {
        this.binariesDirectory = binariesDirectory;
    }

    /**
     * Used so that methods like constructors and static initializers can be found in the graph.
     *
     * @param astMethodName Method name as found in the AST.
     * @return Same name or one of init, clinit as applicable
     */
    public static String toCGMethodName(String astMethodName) {
        if (astMethodName.equals(LocationFinder.CONSTRUCTOR_TAG) ||
                astMethodName.equals(LocationFinder.INITIALIZER_TAG)) {
            return "<init>";
        } else if (astMethodName.equals(LocationFinder.STATIC_INITIALIZER_TAG)) {
            return "<clinit>";
        }

        return astMethodName;
    }

    public ImmutableGraph<String> buildGraph() {
        Stream<String> methodCalls;
        try {
            methodCalls = new JCallGraph().constructCallGraph(findJars())
                    .filter(s -> s.startsWith("M:"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Seq.seq(methodCalls.map(this::parseLine))
                .foldLeft(
                        GraphBuilder.directed().allowsSelfLoops(true).<String>immutable(),
                        (b, t) -> t.map((m1, m2) ->
                                b.addNode(m1).addNode(m2).putEdge(m1, m2)
                        )
                )
                .build();
    }

    private Tuple2<String, String> parseLine(String cgLine) {
        var matcher = lineParser.matcher(cgLine);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("(╯°□°)╯︵ ┻━┻");
        }

        // TODO Removing parameter lists. Using them to diff. overloads will require precise method
        //  finding when matching AST patterns (i.e. resolving parameter types, generics, and anonymous classes)
        var m1 = matcher.group(1);
        var m2 = matcher.group(2);
        var m11 = parameterFinder.matcher(m1).replaceAll("");
        var m22 = parameterFinder.matcher(m2).replaceAll("");
        return tuple(
                m11.replace('$', '.'),
                m22.replace('$', '.')
        );
    }

    private List<Path> findJars() {
        try {
            List<Path> allFiles = Files.list(binariesDirectory).collect(Collectors.toList());
            if (allFiles.stream().anyMatch(p -> !p.toString().endsWith(".jar"))) {
                throw new IllegalStateException("Directory must contain only JAR files: " + binariesDirectory);
            }

            logger.info("Found {} jars at {}", allFiles.size(), binariesDirectory);
            return allFiles;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
