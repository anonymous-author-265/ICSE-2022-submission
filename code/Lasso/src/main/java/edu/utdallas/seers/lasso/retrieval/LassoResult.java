package edu.utdallas.seers.lasso.retrieval;

import com.google.common.collect.ImmutableMap;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.retrieval.RetrievalResult;

import java.util.Collections;
import java.util.Objects;

public class LassoResult extends RetrievalResult<ASTPattern, LassoSummary> {

    private final LassoScore componentScore;

    public LassoResult(ASTPattern result, int rank, LassoScore score, LassoSummary extraStats) {
        super(result, score.value(), rank, extraStats);
        this.componentScore = score;
    }

    public static LassoResult createDummy(ASTPattern.Location location, float score, int rank,
                                          LassoStats stats, BaselineIndexBuilder.Output output) {
        var fakeType = Objects.requireNonNullElse(output, "").toString();
        var pattern = new BaselineIndexBuilder.FakePattern(location, fakeType);
        LassoScore scoreObject = new LassoScore.Builder(ImmutableMap.of(LassoScore.Component.CONSTRAINT_OPERAND, 1.0f))
                .addScore(LassoScore.Component.CONSTRAINT_OPERAND, score)
                .build();

        var finalStats = Objects.requireNonNullElseGet(stats,
                () -> new LassoStats(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList())
        );

        return new LassoResult(pattern, rank, scoreObject,
                new LassoSummary(pattern, finalStats, Collections.emptySet())
        );
    }

    public LassoScore getDecomposedScore() {
        return componentScore;
    }
}
