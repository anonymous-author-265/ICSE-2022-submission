package edu.utdallas.seers.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @param <Q> Type of query.
 * @param <R> Type of results wrapper.
 * @see SimpleResultCollection for a simple version of this class.
 */
public class DefaultResultCollection<Q extends Query, R extends RetrievalResult<?, ?>>
        implements ResultCollection<Q, R> {
    private final ScenarioKey scenarioKey;
    private final Q query;
    private final List<R> results;
    private final List<Integer> truePositiveRanks;
    private final int falseNegativeCount;
    private final int resultCount;
    private final List<String> queryTerms;

    public DefaultResultCollection(ScenarioKey scenarioKey, Q query, List<String> queryTerms, List<R> results) {
        // Rank must be 1-based
        assert results.isEmpty() || results.get(0).getRank() == 1;
        this.scenarioKey = scenarioKey;
        this.query = query;
        this.queryTerms = queryTerms;
        this.results = results;
        resultCount = results.size();
        // TODO maybe make this happen in factory
        this.truePositiveRanks = findTruePositiveRanks(query, results);
        this.falseNegativeCount = query.getGroundTruthIDs().size() - truePositiveRanks.size();
        assert truePositiveRanks.size() <= query.getGroundTruthIDs().size();
    }

    private DefaultResultCollection(DefaultResultCollection<Q, R> other) {
        scenarioKey = other.scenarioKey;
        query = other.query;
        truePositiveRanks = other.truePositiveRanks;
        falseNegativeCount = other.falseNegativeCount;
        resultCount = other.resultCount;
        queryTerms = other.queryTerms;

        results = null;
    }

    protected List<Integer> findTruePositiveRanks(Q query, List<R> results) {
        List<Integer> truePositiveRanks = new ArrayList<>();

        // for each result in the list, check if it is a true positive or a false positive
        for (R r : results) {
            if (query.getGroundTruthIDs().contains(r.getResult().getID())) {
                // if it is a true positive, add its rank to the truePositiveRanks list
                truePositiveRanks.add(r.getRank());
            }
        }

        return truePositiveRanks;
    }

    /**
     * Returns a copy of this object with the result list removed. Calling this method on an object
     * whose results have already been removed is an error.
     *
     * @return A copy of this object without results.
     */
    @Override
    public DefaultResultCollection<Q, R> removeResults() {
        if (results == null) {
            throw new IllegalStateException("Results have already been removed");
        }

        return new DefaultResultCollection<>(this);
    }

    @Override
    public R get(int rank) {
        if (results == null) {
            throw new IllegalArgumentException("Results are not available");
        }

        if (rank <= 0 || rank > resultCount) {
            throw new IllegalArgumentException("Rank must be >=1 and <= result count, got " + rank);
        }

        return results.get(rank - 1);
    }

    @Override
    public Stream<R> items() {
        if (results == null) {
            throw new IllegalArgumentException("Results are not available");
        }

        return results.stream();
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
    public Q getQuery() {
        return query;
    }

    @Override
    public ScenarioKey getScenarioKey() {
        return scenarioKey;
    }

    @Override
    public List<String> getQueryTerms() {
        return queryTerms;
    }
}
