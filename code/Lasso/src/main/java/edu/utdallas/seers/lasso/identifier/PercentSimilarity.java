package edu.utdallas.seers.lasso.identifier;

import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;

public class PercentSimilarity extends SimilarityBase {
    @Override
    protected double score(BasicStats stats, double freq, double docLen) {
        return 1 / docLen;
    }

    @Override
    public String toString() {
        return "PercentSimilarity";
    }
}
