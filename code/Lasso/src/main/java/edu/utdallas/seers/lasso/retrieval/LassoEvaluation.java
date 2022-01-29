package edu.utdallas.seers.lasso.retrieval;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.retrieval.RetrievalEvaluation;
import edu.utdallas.seers.retrieval.RetrievalResult;
import org.jooq.lambda.tuple.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

import static edu.utdallas.seers.collection.Collections.streamMap;
import static org.jooq.lambda.tuple.Tuple.tuple;

// TODO add type parameters of key type and extra info type
public class LassoEvaluation extends RetrievalEvaluation<PatternEntry, LassoResult> {

    public final int fullPatternMatches;
    public final int fullQueryMatches;
    public final int partialQueryMatches;
    public final List<Tuple2<Integer, Integer>> scoreClusters;
    public final Map<Set<String>, List<LassoResult>> operandClusters;
    public List<Integer> gtMethodESCRanks;
    public Integer gtMethodRank;
    public int gtGroupSize;

    public LassoEvaluation(LassoResultCollection collection) {
        super(collection);
        fullQueryMatches = (int) collection.items()
                .filter(r1 -> r1.getDecomposedScore().queryOp == r1.getDecomposedScore().qopTotal)
                .count();
        partialQueryMatches = (int) collection.items()
                .filter(r1 -> r1.getDecomposedScore().queryOp < r1.getDecomposedScore().qopTotal)
                .count();
        fullPatternMatches = (int) collection.items()
                .filter(r -> r.getDecomposedScore().patternOp == r.getDecomposedScore().popTotal)
                .count();
        var map = collection.items()
                .collect(Collectors.groupingBy(RetrievalResult::getScore));
        scoreClusters = streamMap(map)
                .filter((s, rs) -> rs.size() > 1)
                .sortedComparingReversed((s, rs) -> s)
                .combine((s, rs) -> {
                    var srs =
                            ImmutableList.sortedCopyOf(Comparator.comparing(RetrievalResult::getRank), rs);
                    return tuple(srs.get(0).getRank(), srs.get(srs.size() - 1).getRank());
                })
                .toUnmodifiableList();
        operandClusters = ImmutableMap.copyOf(collection.items()
                .collect(Collectors.groupingBy(r -> r.getResult().getOperands().stream()
                        .map(o -> o.text)
                        .collect(Collectors.toSet()))
                )
        );
        var gtStats = Optional.of(collection.getTruePositiveRanks())
                .filter(rs -> !rs.isEmpty())
                .map(rs -> collection.getCluster(rs.get(0)).results.get(0).getStats());
        gtMethodRank = gtStats
                .map(LassoSummary::getGroupRank)
                .orElse(null);
        gtGroupSize = gtStats
                .map(s -> s.groupedResults.size())
                .orElse(-1);
        gtMethodESCRanks = gtStats
                .map(s -> s.groupedResults.stream().map(RetrievalResult::getRank).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private LassoEvaluation(LassoEvaluation other) {
        super(other.collection.removeResults());
        fullPatternMatches = other.fullPatternMatches;
        fullQueryMatches = other.fullQueryMatches;
        partialQueryMatches = other.partialQueryMatches;
        scoreClusters = other.scoreClusters;
        operandClusters = other.operandClusters;
    }

    @Override
    public LassoResultCollection getResults() {
        return (LassoResultCollection) super.getResults();
    }

    @Override
    public LassoEvaluation removeResults() {
        return new LassoEvaluation(this);
    }
}
