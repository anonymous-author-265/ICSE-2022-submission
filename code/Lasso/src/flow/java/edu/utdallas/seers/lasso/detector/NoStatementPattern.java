package edu.utdallas.seers.lasso.detector;

import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.Pattern;
import edu.utdallas.seers.lasso.data.entity.PatternSingleLineFormat;

import java.util.Optional;


// FIXME this class will not be needed after the Pattern class is unified and can hold multiple lines
public class NoStatementPattern extends Pattern {

    private final ASTPattern astPattern;

    protected NoStatementPattern(ASTPattern astPattern) {
        super(null, astPattern.getPatternType());
        this.astPattern = astPattern;
    }

    @Override
    public Optional<PatternSingleLineFormat> toSingleLineFormat() {
        int line = astPattern.getLines().stream()
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Should have at least one line " +
                        astPattern.getFileName() + " " + astPattern.getPatternType()));

        return Optional.of(new PatternSingleLineFormat(
                astPattern.getFileName(),
                // FIXME should be able to return all lines
                line,
                true,
                astPattern.getPatternType()));
    }
}
