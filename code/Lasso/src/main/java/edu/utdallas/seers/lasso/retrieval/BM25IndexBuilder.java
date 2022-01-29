package edu.utdallas.seers.lasso.retrieval;

import java.nio.file.Path;

public class BM25IndexBuilder extends BaselineIndexBuilder {

    public BM25IndexBuilder(Path sourcesPath, LassoScenarioID<BaselineConfig> key) {
        super(sourcesPath, key);
    }

    public static BM25Index createIndex(Path path, LassoScenarioID<BaselineConfig> config) {
        return new BM25IndexBuilder(path, config).buildIndex();
    }

    @Override
    protected BM25Index createIndex(Path indexPath) {
        return new BM25Index(indexPath, key);
    }

    @Override
    public BM25Index buildIndex() {
        return (BM25Index) super.buildIndex();
    }

    public static class BM25Index extends BaselineIndex {
        public BM25Index(Path path, LassoScenarioID<BaselineConfig> key) {
            super(path, key);
        }
    }
}
