package edu.utdallas.seers.retrieval;

import edu.utdallas.seers.parameter.Options;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.utdallas.seers.collection.Collections.transformMap;

public class AggregatedRetrievalEvaluation {

    private final Map<Integer, Integer> hitsAtKAccum = new HashMap<>(
            Options.getInstance().getHitsAtKRanks().stream()
                    .collect(Collectors.toMap(
                            i -> i,
                            i -> 0
                    ))
    );
    private int counter = 0;
    private float recallAccum = 0;
    private float rrAccum = 0;
    private float apAccum = 0;
    private float resultsAccum = 0;
    private float rankAccum = 0;
    private float resultSizeAccum = 0;

    public static <E extends RetrievalEvaluation<?, ?>>
    AggregatedRetrievalEvaluation create(List<E> evaluations) {
        var accumulator = new AggregatedRetrievalEvaluation();
        for (RetrievalEvaluation<?, ?> eval : evaluations) {
            accumulator.accumulate(eval);
        }

        return accumulator;
    }

    public static AggregatedRetrievalEvaluation aggregate(List<AggregatedRetrievalEvaluation> es) {
        var accum = new AggregatedRetrievalEvaluation();
        for (AggregatedRetrievalEvaluation e : es) {
            accum.accumulate(e);
        }

        return accum;
    }

    private void accumulate(AggregatedRetrievalEvaluation e) {
        counter += e.counter;
        recallAccum += e.recallAccum;
        rrAccum += e.rrAccum;
        apAccum += e.apAccum;
        resultsAccum += e.resultsAccum;
        rankAccum += e.rankAccum;
        hitsAtKAccum.replaceAll((k, c) -> c + e.hitsAtKAccum.get(k));
        resultSizeAccum += e.resultSizeAccum;
    }

    private void accumulate(RetrievalEvaluation<?, ?> eval) {
        counter++;
        recallAccum += eval.getRecall();
        rrAccum += eval.getReciprocalRank();
        apAccum += eval.getAveragePrecision();
        resultsAccum += eval.getTotalResults();
        rankAccum += eval.getRank().orElse(0);
        eval.getRank()
                .ifPresent(r -> hitsAtKAccum.replaceAll((k, c) -> {
                    if (r <= k) return c + 1;
                    return c;
                }));
        resultSizeAccum += eval.getAverageResultSize();
    }

    private float calculateAverage(float accum) {
        if (counter == 0) {
            throw new IllegalStateException("Has not accumulated any results");
        }

        return accum / counter;
    }

    public float getAverageRecall() {
        return calculateAverage(recallAccum);
    }

    public float getMrr() {
        return calculateAverage(rrAccum);
    }

    public float getMap() {
        return calculateAverage(apAccum);
    }

    public float getAverageResults() {
        return calculateAverage(resultsAccum);
    }

    public Map<Integer, Float> getPercentHitsAtK() {
        return transformMap(hitsAtKAccum, i -> i, i -> (float) i / counter);
    }

    public Map<Integer, Integer> getHitsAtK() {
        return transformMap(hitsAtKAccum, i -> i, i -> i);
    }

    public int getQueryCount() {
        return counter;
    }

    public float getAverageRank() {
        return rankAccum / (counter * getAverageRecall());
    }

    public float getMeanAverageResultSize() {
        return calculateAverage(resultSizeAccum);
    }
}
