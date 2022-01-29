package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.ipa.slicer.Statement;
import edu.utdallas.seers.lasso.ast.PatternStore;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.Pattern;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.lasso.data.entity.SimplePattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

// TODO this class can be made to inherit from OneInputDetector but we will need to change the return types
public class IfChainDetector {
    List<Pattern> detectPattern(Slice slice, PatternStore patternStore) {
        HashSet<ASTPattern> patternSet = new HashSet<>();
        for (Statement s : slice.getSliceStatements()) {
            Pattern temp = new SimplePattern(s, PatternType.IF_CHAIN);
            Optional<ASTPattern> astPattern = patternStore.lookUpInstance(temp);
            astPattern.ifPresent(patternSet::add);
        }

        List<Pattern> patterns = new ArrayList<>();
        for (ASTPattern p : patternSet) {
            patterns.add(new NoStatementPattern(p));
        }
        return patterns;
    }
}