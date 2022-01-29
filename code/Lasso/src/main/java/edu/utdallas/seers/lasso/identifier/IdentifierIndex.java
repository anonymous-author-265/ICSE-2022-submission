package edu.utdallas.seers.lasso.identifier;

import edu.utdallas.seers.lasso.data.entity.variables.Attribute;
import edu.utdallas.seers.retrieval.Index;
import org.apache.lucene.search.similarities.Similarity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IdentifierIndex extends Index<Attribute> {

    private final Map<String, Attribute> identifiers;

    /**
     * TODO this dependency could be removed by merging Index builder and Index or by adding the fields
     * to the entity being indexed
     */
    private final String idFieldName;
    private final Map<String, List<String>> preprocessedTexts;

    public IdentifierIndex(Path path, Map<String, Attribute> identifiers, String idFieldName,
                           Map<String, List<String>> preprocessedTexts) {
        super(path);
        this.identifiers = identifiers;
        this.idFieldName = idFieldName;
        this.preprocessedTexts = preprocessedTexts;
    }

    public List<String> findPreprocessedText(String identifier) {
        return preprocessedTexts.get(identifier);
    }

    @Override
    protected Attribute loadEntity(ScoredDocID sd) {
        return identifiers.get(sd.getId());
    }

    @Override
    protected String getIDFieldName() {
        return idFieldName;
    }

    @Override
    protected Optional<Similarity> getSimilarity() {
        return Optional.of(new PercentSimilarity());
    }
}
