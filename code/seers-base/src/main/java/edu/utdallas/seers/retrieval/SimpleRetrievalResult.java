package edu.utdallas.seers.retrieval;

public class SimpleRetrievalResult<T extends Retrievable> extends RetrievalResult<T, Void> {

    public SimpleRetrievalResult(T result, int rank, float score) {
        super(result, score, rank, null);
    }

    @Override
    public Void getStats() {
        throw new IllegalStateException("This class does not provide extra stats");
    }
}
