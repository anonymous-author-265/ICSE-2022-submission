package edu.utdallas.seers.lasso.retrieval;

import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;

import java.nio.file.Path;
import java.util.Optional;

public class TFIDFIndexBuilder extends BaselineIndexBuilder {
    public TFIDFIndexBuilder(Path sourcesPath, LassoScenarioID<BaselineConfig> key) {
        super(sourcesPath, key);
    }

    public static TFIDFIndex createIndex(Path path, LassoScenarioID<BaselineConfig> config) {
        return new TFIDFIndexBuilder(path, config).buildIndex();
    }

    @Override
    protected TFIDFIndex createIndex(Path indexPath) {
        return new TFIDFIndex(indexPath, key);
    }

    @Override
    protected Optional<Similarity> getSimilarity() {
        return Optional.of(new ClassicSimilarity());
    }

    @Override
    public TFIDFIndex buildIndex() {
        return (TFIDFIndex) super.buildIndex();
    }

    public static class TFIDFIndex extends BaselineIndex {
        protected TFIDFIndex(Path path, LassoScenarioID<BaselineConfig> key) {
            super(path, key);
        }

        @Override
        protected Optional<Similarity> getSimilarity() {
            return Optional.of(new ClassicSimilarity());
        }
    }
}
