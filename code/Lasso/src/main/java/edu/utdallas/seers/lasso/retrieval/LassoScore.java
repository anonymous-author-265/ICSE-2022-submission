package edu.utdallas.seers.lasso.retrieval;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static edu.utdallas.seers.collection.Collections.streamMap;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class LassoScore {

    public final int queryOp;
    public final int qopTotal;
    public final int patternOp;
    public final int popTotal;
    private final Map<Component, Float> weights;
    private final Map<Component, Float> values;
    private final Float penaltyFactor;

    public LassoScore(Map<Component, Float> weights, Map<Component, Float> values,
                      int queryOp, int qopTotal, int patternOp, int popTotal) {
        this.weights = weights;
        this.values = values;
        this.queryOp = queryOp;
        this.qopTotal = qopTotal;
        this.patternOp = patternOp;
        this.popTotal = popTotal;
        penaltyFactor = null;
    }

    private LassoScore(LassoScore other, float penaltyFactor) {
        weights = other.weights;
        values = other.values;
        this.penaltyFactor = penaltyFactor;
        queryOp = other.queryOp;
        qopTotal = other.qopTotal;
        patternOp = other.patternOp;
        popTotal = other.popTotal;
    }

    public static String repr(Map<Component, Float> scoreWeights) {
        return Arrays.stream(Component.values())
                .filter(scoreWeights::containsKey)
                .map(c -> String.format("%s-%.2f", c.abbreviate(), scoreWeights.get(c)))
                .collect(Collectors.joining("_"));
    }

    public LassoScore penalize(float penaltyFactor) {
        if (penaltyFactor < 0 || penaltyFactor > 1) {
            throw new IllegalArgumentException("Penalty factor must be between 0.0 and 1.0, was: " + penaltyFactor);
        }

        return new LassoScore(this, penaltyFactor);
    }

    public String repr() {
        return repr(streamMap(values)
                .combine((c, v) -> tuple(c, v * weights.getOrDefault(c, 0f)))
                .toMap(Tuple2::v1, Tuple2::v2));
    }

    public float value() {
        float penalty = Optional.ofNullable(penaltyFactor).orElse(1.0f);

        return penalty *
                (float) streamMap(values)
                        .combine((c, v) -> (double) v * weights.get(c))
                        .sumDouble(v -> v);
    }

    @Deprecated
    public boolean hasCore() {
        return streamMap(values)
                .anyMatch(p -> p.v1.isCore && p.v2 > 0);
    }

    public enum Component {
        CONSTRAINT_OPERAND(true),
        ESC_OPERAND(true),
        EXPECTED_CIP(true),
        OT_METHOD_NAME(true),
        OT_CLASS_NAME(true),
        TEXT_BLOCK(true),
        CQ_BLOCK(true),
        OP_BLOCK(true),
        CONTEXT_METHOD(true),

        KT_ESC(false),
        KT_DDS(false),
        KT_METHOD_TEXT(false),
        CT_METHOD_TEXT(false),
        ESC_MATCH_PC(false),
        CT_METHOD_NAME(false),
        CT_CLASS_NAME(false);

        static {
            var clashes = Seq.seq(Arrays.asList(values()))
                    .grouped(Component::abbreviate)
                    .map(p -> tuple(p.v1, p.v2.toList()))
                    .filter(p -> p.v2.size() > 1)
                    .toList();

            if (!clashes.isEmpty()) {
                throw new IllegalStateException("Abbreviation clashes for score components: " +
                        clashes);
            }
        }

        // TODO remove, this distinction will stop existing in 1.0
        private final boolean isCore;

        Component(boolean isCore) {
            this.isCore = isCore;
        }

        private String abbreviate() {
            return Arrays.stream(toString().split("_"))
                    .map(s -> s.substring(0, 1))
                    .collect(Collectors.joining());
        }
    }

    public static class Builder {

        private final Map<Component, Float> weights;
        private final HashMap<Component, Float> scores;
        private int queryOp;
        private int patternOp;
        private int qopTotal;
        private int popTotal;

        public Builder(Map<Component, Float> weights) {
            this.weights = weights;
            scores = new HashMap<>();
        }

        public Builder addScore(Component c, float value) {
            // Do not count the score if we don't have a weight for it
            if (!weights.containsKey(c)) {
                return this;
            }

            scores.put(c, value);

            return this;
        }

        public Builder addOperandMatchCount(int queryOp, int qopTotal, int patternOp, int popTotal) {
            this.queryOp = queryOp;
            this.patternOp = patternOp;
            this.qopTotal = qopTotal;
            this.popTotal = popTotal;

            return this;
        }

        public LassoScore build() {
            return new LassoScore(weights, scores, queryOp, qopTotal, patternOp, popTotal);
        }
    }
}
