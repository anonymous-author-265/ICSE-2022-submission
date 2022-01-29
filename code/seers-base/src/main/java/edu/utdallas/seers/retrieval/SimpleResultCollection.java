package edu.utdallas.seers.retrieval;

import java.util.List;

/**
 * Class used to simplify the type parameters of {@link DefaultResultCollection}.
 *
 * @param <Q> Type of query.
 * @param <R> Type of results.
 */
public class SimpleResultCollection<Q extends Query, R extends Retrievable>
        extends DefaultResultCollection<Q, SimpleRetrievalResult<R>> {

    public SimpleResultCollection(ScenarioKey scenarioKey, Q query, List<String> queryTerms, List<SimpleRetrievalResult<R>> results) {
        super(scenarioKey, query, queryTerms, results);
    }
}
