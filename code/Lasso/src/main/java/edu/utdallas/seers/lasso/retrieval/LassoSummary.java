package edu.utdallas.seers.lasso.retrieval;

import com.google.common.collect.Sets;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;

import java.util.*;

public class LassoSummary {

    public final Map<String, Set<Integer>> qtLocations = Collections.emptyMap();
    public final Set<String> consequenceDocTerms;
    public final List<LassoResult> groupedResults;
    private int groupRank = -1;

    public LassoSummary(ASTPattern astPattern, LassoStats stats, Set<String> consequenceTerms) {
        consequenceDocTerms = new HashSet<>(Sets.intersection(consequenceTerms, stats.docCommentTerms));
        groupedResults = stats.groupedResults;
    }

    public void findGroupRank(Set<String> gtLines) {
        this.groupRank = groupedResults.stream()
                .filter(r -> {
                            var location = r.getResult().location;
                            return location.getLineNumbers().stream()
                                    .map(i -> location.packagePath + ":" + i)
                                    .anyMatch(gtLines::contains);
                        }
                )
                .findFirst()
                .map(o -> groupedResults.indexOf(o) + 1)
                // Can be absent if method was retrieved but pattern wasn't
                .orElse(-1);
    }

    public int getGroupRank() {
        return groupRank;
    }
}
