package edu.utdallas.seers.lasso.retrieval;

import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;

/**
 * Returns 1 for every term that is in the document.
 */
public class SimpleCountSimilarity extends SimilarityBase {
    @Override
    protected double score(BasicStats stats, double freq, double docLen) {
        return 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
