package edu.utdallas.seers.lasso.retrieval;

import java.util.Map;
import java.util.Objects;

public class LassoConfig implements RetrievalConfiguration {
    @Deprecated
    public final int windowSize;
    public final boolean methodGranularity;
    public final boolean allMethods;
    public final boolean baselineCombination;
    public final boolean baselineBoost;
    public final Map<LassoScore.Component, Float> scoreWeights;
    public final float rankPenaltyPercent;
    public final float callGraphPenaltyFactor;
    public final BaselineIndexBuilder.Type underlyingType;

    LassoConfig(int windowSize, boolean methodGranularity, boolean allMethods, boolean baselineCombination,
                boolean baselineBoost, Map<LassoScore.Component, Float> scoreWeights,
                float rankPenaltyPercent, float callGraphPenaltyFactor, BaselineIndexBuilder.Type underlyingType) {
        this.windowSize = windowSize;
        this.methodGranularity = methodGranularity;
        this.allMethods = allMethods;
        this.baselineCombination = baselineCombination;
        this.baselineBoost = baselineBoost;
        this.scoreWeights = scoreWeights;
        this.rankPenaltyPercent = rankPenaltyPercent;
        this.callGraphPenaltyFactor = callGraphPenaltyFactor;
        this.underlyingType = underlyingType;
    }

    @Override
    public String toString() {
        var lasso13 = "Lasso-13";
        if (underlyingType.equals(BaselineIndexBuilder.Type.BM25)) {
            return lasso13 + "Luc";
        }

        return lasso13 + underlyingType.prettyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LassoConfig that = (LassoConfig) o;
        return windowSize == that.windowSize && methodGranularity == that.methodGranularity &&
                allMethods == that.allMethods && baselineCombination == that.baselineCombination &&
                baselineBoost == that.baselineBoost && Float.compare(that.rankPenaltyPercent, rankPenaltyPercent) == 0 &&
                Float.compare(that.callGraphPenaltyFactor, callGraphPenaltyFactor) == 0 &&
                Objects.equals(scoreWeights, that.scoreWeights) && underlyingType == that.underlyingType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowSize, methodGranularity, allMethods, baselineCombination,
                baselineBoost, scoreWeights, rankPenaltyPercent, callGraphPenaltyFactor, underlyingType);
    }
}
