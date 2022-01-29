package edu.utdallas.seers.retrieval;

import java.util.Optional;
import java.util.stream.Stream;

public class RetrievalEvaluation<Q extends Query, R extends RetrievalResult<?, ?>> {
    protected final ResultCollection<Q, R> collection;

    public RetrievalEvaluation(ResultCollection<Q, R> collection) {
        this.collection = collection;
    }

    /**
     * Returns a copy of this object with the result list removed.
     * Used to save memory when computing aggregated results.
     *
     * @return A copy of this object without result list.
     */
    public RetrievalEvaluation<Q, R> removeResults() {
        return new RetrievalEvaluation<>(collection.removeResults());
    }

    public R get(int rank) {
        return collection.get(rank);
    }

    public Stream<R> items() {
        return collection.items();
    }

    public float getAverageResultSize() {
        return collection.getAverageResultSize();
    }

    public ScenarioKey getScenarioKey() {
        return collection.getScenarioKey();
    }

    public int getTotalResults() {
        return collection.getResultCount();
    }

    public Q getQuery() {
        return collection.getQuery();
    }

    public Optional<Integer> getRank() {
        return Optional.of(!collection.getTruePositiveRanks().isEmpty())
                .filter(v -> v)
                .map(v -> collection.getTruePositiveRanks().get(0));
    }

    public float getReciprocalRank() {
        return getRank()
                .map(r -> 1f / r)
                .orElse(0f);
    }

    public int getTruePositiveCount() {
        return collection.getTruePositiveRanks().size();
    }

    public float getPrecision() {
        var truePositives = getTruePositiveCount();
        return truePositives > 0 ? ((float) truePositives / collection.getResultCount()) : 0;
    }

    public float getRecall() {
        var truePositives = getTruePositiveCount();
        var totalRelevant = truePositives + collection.getFalseNegativeCount();
        return (float) truePositives / totalRelevant;
    }

    public float getF1() {
        var precision = getPrecision();
        var recall = getRecall();
        return (2 * precision * recall) / (precision + recall);
    }

    public float getAveragePrecision() {
        int numTP = 0;
        double sumPrecs = 0.0;
        for (int rk : collection.getTruePositiveRanks()) {
            numTP++;
            double precAtRk = ((double) numTP) / rk;
            sumPrecs += precAtRk;
        }

        float totalRelevant = collection.getFalseNegativeCount() + numTP;
        return (float) (totalRelevant == 0.0 ? 0.0 : sumPrecs / totalRelevant);
    }

    public ResultCollection<Q, R> getResults() {
        return collection;
    }
}
