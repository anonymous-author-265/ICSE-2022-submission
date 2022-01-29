package edu.utdallas.seers.lasso.detector;

import edu.utdallas.seers.lasso.ast.PatternStore;
import edu.utdallas.seers.lasso.data.entity.NameValueASTPattern;
import edu.utdallas.seers.lasso.data.entity.Pattern;
import edu.utdallas.seers.lasso.data.entity.PatternSingleLineFormat;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.lasso.data.entity.constants.Constant;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;
import org.jooq.lambda.tuple.Tuple2;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Combines patterns detected in AST with patterns found using slicing.
 */
public abstract class ASTUnionDetector {
    protected List<Pattern> combinePatterns(List<Pattern> slicingPatterns, PatternStore patternStore,
                                            Constant<?> constant, Attribute attribute) {
        var astPatterns = patternStore.lookUpInstances(getPatternType(), constant, attribute).stream()
                /* Some slicing patterns may refer to a single line of a multi-line-pattern
                 *  Extract each line of each pattern so that we can find them individually
                 * They are uniquely filtered at the end to avoid returning duplicates */
                .flatMap(p -> p.getLines().stream()
                        .map(l -> Tuple.tuple(
                                new PatternSingleLineFormat(p.getFileName(), l, true, getPatternType()),
                                p
                        ))
                )
                .collect(Collectors.toMap(
                        Tuple2::v1,
                        Tuple2::v2
                ));

        for (Pattern slicingPattern : slicingPatterns) {
            Optional<PatternSingleLineFormat> maybe = slicingPattern.toSingleLineFormat();
            if (maybe.isEmpty()) {
                continue;
            }

            PatternSingleLineFormat key = maybe.get();
            if (!astPatterns.containsKey(key)) {
                astPatterns.put(key, new NameValueASTPattern(
                        null,
                        getPatternType(),
                        Collections.emptyList(), constant,
                        attribute
                ));
            }
        }

        return astPatterns.values().stream()
                .distinct()
                .map(NoStatementPattern::new)
                .collect(Collectors.toList());
    }

    protected abstract PatternType getPatternType();


}
