package edu.utdallas.seers.lasso.retrieval;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.utdallas.seers.collection.Collections.streamMap;

public class LassoStats {

    public final int operandCount;
    /**
     * From operand index (1-based) to size.
     */
    public final Map<Integer, Integer> operandSizes;
    public final Set<String> docCommentTerms;
    public final List<LassoResult> groupedResults;

    public LassoStats(Map<Integer, Integer> operandSizes, Map<String, List<Integer>> methodTextIndex,
                      List<LassoResult> groupedResults) {
        this.operandCount = operandSizes.size();
        this.operandSizes = ImmutableMap.copyOf(operandSizes);
        docCommentTerms = streamMap(methodTextIndex)
                .filter((w, is) -> is.contains(-1))
                .combine((w, is) -> w)
                .toSet();
        this.groupedResults = ImmutableList.copyOf(groupedResults);
    }
}
