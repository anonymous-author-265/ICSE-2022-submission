package edu.utdallas.seers.retrieval;

import java.util.List;
import java.util.stream.Stream;

public interface ResultCollection<Q extends Query, R extends RetrievalResult<?, ?>> {
    ResultCollection<Q, R> removeResults();

    R get(int rank);

    Stream<R> items();

    default float getAverageResultSize() {
        return 0;
    }

    int getResultCount();

    int getFalseNegativeCount();

    List<Integer> getTruePositiveRanks();

    Q getQuery();

    ScenarioKey getScenarioKey();

    List<String> getQueryTerms();
}
