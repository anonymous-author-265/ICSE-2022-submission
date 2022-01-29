package edu.utdallas.seers.lasso.retrieval;

import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.retrieval.ResultCollection;
import edu.utdallas.seers.retrieval.RetrievalResult;
import edu.utdallas.seers.stream.PairSeq;
import org.jooq.lambda.Seq;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;

// TODO maybe generalize as "Clustered result collection"
public class LassoResultCollection implements ResultCollection<PatternEntry, LassoResult> {

    private final List<ResultGroup> results;
    private final int resultCount;
    private final int falseNegativeCount;
    private final List<Integer> truePositiveRanks;
    private final PatternEntry query;
    private final LassoScenarioID scenarioKey;
    private final List<String> queryTerms;
    private float averageResultSize;

    public LassoResultCollection(
            LassoScenarioID key,
            PatternEntry query,
            List<String> queryTerms, List<LassoResult> retrievalResults,
            boolean cluster) {
        this.query = query;
        scenarioKey = key;
        this.queryTerms = queryTerms;
        this.results = clusterResults(retrievalResults, cluster);
        resultCount = results.size();
        truePositiveRanks = findTruePositiveRanks(query, results);
        falseNegativeCount = query.getGroundTruthIDs().size() - truePositiveRanks.size();
        averageResultSize = Seq.seq(retrievalResults)
                .map(r -> {
                    var range = r.getResult().location.range;
                    return (float) (range.end.line - range.begin.line + 1);
                })
                .avg()
                .orElse(0f);
    }

    private LassoResultCollection(LassoResultCollection other) {
        query = other.query;
        scenarioKey = other.scenarioKey;
        queryTerms = other.queryTerms;
        resultCount = other.resultCount;
        truePositiveRanks = other.truePositiveRanks;
        falseNegativeCount = other.falseNegativeCount;
        averageResultSize = other.averageResultSize;

        results = null;
    }

    public ResultGroup getCluster(int rank) {
        return results.get(rank - 1);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    // TODO move this clustering to the index, where it is already happening, but only for method level result
    private List<ResultGroup> clusterResults(List<LassoResult> individualResults, boolean cluster) {
        if (!cluster) {
            return PairSeq.zipWithIndex(individualResults)
                    .combine((r, i) -> new ResultGroup(i.intValue() + 1, Collections.singletonList(r)))
                    .toList();
        }

        // All results with same operand texts will be in the same group
        var groups = individualResults.stream().collect(Collectors.groupingBy(r ->
                r.getResult().getOperands().stream().map(o -> o.text).collect(Collectors.toSet()))
        );

        var sortedGroups = streamMap(groups)
                // Each group will be represented by the result with the lowest rank
                .sortedComparing((os, rs) -> Seq.seq(rs).min(RetrievalResult::getRank).get())
                .combine((os, rs) -> rs);

        return PairSeq.zipWithIndex(sortedGroups)
                .combine((rs, i) -> new ResultGroup(i.intValue() + 1, rs))
                .toList();
    }

    /**
     * Each ground truth can have multiple lines, as can each pattern result. This override makes
     * it so that only the first pattern that has a line and file in common with one of the ground
     * truths is counted as a true positive. Otherwise, we could have more true positives than
     * ground truths, as multiple patterns can be on the same line.
     *
     * @param query   A query.
     * @param results The retrieval results.
     * @return The ranks of the true positives in the result list.
     */
    protected List<Integer> findTruePositiveRanks(PatternEntry query, List<ResultGroup> results) {
        // TODO are we using multiple ground truths for any constraint?
        var groundTruths = query.getGroundTruthIDs().stream()
                .map(s -> {
                    var split = s.split(":");
                    return Arrays.stream(split[1].split(","))
                            .map(n -> split[0] + ":" + n)
                            .collect(Collectors.toSet());
                })
                .collect(Collectors.toList());

        var ranks = new ArrayList<Integer>();

        for (ResultGroup group : results) {
            if (groundTruths.isEmpty()) break;

            var groupLines = group.lines;

            Seq.seq(groundTruths)
                    .findFirst(ls -> ls.stream().anyMatch(groupLines::contains))
                    .ifPresent(gt -> {
                        groundTruths.remove(gt);
                        ranks.add(group.rank);
                        if (!group.results.get(0).getStats().groupedResults.isEmpty()) {
                            group.results.get(0).getStats().findGroupRank(gt);
                        }
                    });
        }

        return ranks;
    }

    public Stream<ResultGroup> clusteredItems() {
        return results.stream();
    }

    @Override
    public LassoResultCollection removeResults() {
        return new LassoResultCollection(this);
    }

    @Override
    public LassoResult get(int rank) {
        if (results == null) {
            throw new IllegalArgumentException("Results are not available");
        }

        if (rank < 1 || rank > results.size()) {
            throw new IllegalArgumentException("Rank must be between 1 and # of results");
        }

        return results.get(rank - 1).results.get(0);
    }

    @Override
    public Stream<LassoResult> items() {
        return results.stream().map(g -> g.results.get(0));
    }

    @Override
    public int getResultCount() {
        return resultCount;
    }

    @Override
    public int getFalseNegativeCount() {
        return falseNegativeCount;
    }

    @Override
    public List<Integer> getTruePositiveRanks() {
        return truePositiveRanks;
    }

    @Override
    public PatternEntry getQuery() {
        return query;
    }

    @Override
    public LassoScenarioID getScenarioKey() {
        return scenarioKey;
    }

    @Override
    public List<String> getQueryTerms() {
        return queryTerms;
    }

    @Override
    public float getAverageResultSize() {
        return averageResultSize;
    }

    public static class ResultGroup {

        public final int rank;
        public final List<LassoResult> results;
        public final Set<String> lines;

        public ResultGroup(int rank, List<LassoResult> results) {
            this.rank = rank;
            this.results = Seq.seq(results).sorted(RetrievalResult::getRank).toUnmodifiableList();
            lines = PairSeq.seq(results, r -> r.getResult().getFileName(), r -> r.getResult().getLines())
                    .flatMap((f, ls) -> ls.stream()
                            .map(i -> f + ":" + i)
                    )
                    .toSet();
        }
    }
}
