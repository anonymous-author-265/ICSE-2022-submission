package edu.utdallas.seers.lasso.retrieval;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.retrieval.Index;
import edu.utdallas.seers.stream.PairSeq;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.lucene.document.Document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

public abstract class BaselineIndex extends Index<BaselineIndexBuilder.TextBlock> {
    protected final LassoScenarioID<BaselineConfig> key;
    protected final TextPreprocessor preprocessor = LassoIndexBuilder.createPreprocessor();

    protected BaselineIndex(Path path, LassoScenarioID<BaselineConfig> key) {
        super(path);
        this.key = key;
    }

    protected BaselineIndex(LassoScenarioID<BaselineConfig> key) {
        super();
        this.key = key;
    }

    public LassoResultCollection search(PatternEntry constraint) {
        var rawText = key.getConfiguration().input.extractor.apply(constraint);
        var text = preprocessor.preprocess(rawText, true)
                .collect(Collectors.joining(" "));

        var results = PairSeq.zipWithIndex(search(text, BaselineIndexBuilder.CONTENT_FIELD_NAME))
                .combine((r, i) -> {
                    var block = r.getResult();
                    // TODO all of this is done to adapt the baseline to work with the Pattern*
                    //  classes. Should be done better in the future.
                    var range = new Range(
                            new Position(block.lineBegin, 1),
                            new Position(block.lineEnd, 1)
                    );
                    var location = new ASTPattern.Location(Path.of(block.fileName), block.fileName,
                            range, range, null, null);

                    return LassoResult.createDummy(location, r.getScore(), i.intValue() + 1,
                            null, key.getConfiguration().output);
                })
                .toList();

        return new LassoResultCollection(key, constraint, Collections.emptyList(), results, false);
    }

    @Override
    protected BaselineIndexBuilder.TextBlock loadEntity(ScoredDocID sd) {
        Document doc;
        try {
            doc = searcher.doc(sd.getIndexID());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new BaselineIndexBuilder.TextBlock(doc.get(BaselineIndexBuilder.FILE_FIELD_NAME),
                Integer.parseInt(doc.get(BaselineIndexBuilder.BEGIN_FIELD_NAME)), Integer.parseInt(doc.get(BaselineIndexBuilder.END_FIELD_NAME)),
                null);
    }

    @Override
    protected String getIDFieldName() {
        return "id";
    }
}
