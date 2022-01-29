package edu.utdallas.seers.retrieval;

import java.util.Objects;

/**
 * @param <T> Type of results.
 * @param <S> Type of extra information for this result.
 */
public class RetrievalResult<T extends Retrievable, S> {
    private final T result;
    private final float score;
    private final S stats;
    private int rank;

    public RetrievalResult(T result, float score, int rank, S stats) {
        this.result = result;
        this.score = score;
        this.rank = rank;
        this.stats = stats;
    }

    public void reRank(int newRank) {
        rank = newRank;
    }

    // TODO: these eq and hc work contingent on whether we return only distinct results. If that's
    //  not the case for some reason, this must be changed.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetrievalResult<?, ?> that = (RetrievalResult<?, ?>) o;
        return rank == that.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank);
    }

    @Override
    public String toString() {
        return "RetrievalResult{" +
                "result=" + result +
                ", score=" + score +
                ", stats=" + stats +
                ", rank=" + rank +
                '}';
    }

    public T getResult() {
        return result;
    }

    public float getScore() {
        return score;
    }

    public int getRank() {
        return rank;
    }

    public S getStats() {
        return stats;
    }
}
