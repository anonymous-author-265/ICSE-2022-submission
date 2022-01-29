package edu.utdallas.seers.lasso.retrieval;


import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This class manages access to Lucene indexes on disk so that they are not written by multiple
 * threads at the same time. Indexes can be read concurrently after they have been created.
 */
public class IndexCoordinator {
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> baselineLocks = new ConcurrentHashMap<>();

    public LassoIndex createIndex(Path sourcesDir, LassoScenarioID<LassoConfig> scenario) {
        var indexName = LassoIndexBuilder.createIndexName(scenario);
        synchronized (
                locks.computeIfAbsent(indexName, k -> new Object())
        ) {
            return new LassoIndexBuilder(sourcesDir, scenario, this)
                    .createIndex();
        }
    }

    // FIXME might not need to be synced because there is only one baseline per type per system
    public BaselineIndex createBaselineIndex(Path sourcesDir, LassoScenarioID<BaselineConfig> key) {
        var name = key.getConfiguration().type.nameFactory.apply(key);

        synchronized (baselineLocks.computeIfAbsent(name, k -> new Object())) {
            return key.getConfiguration().type.indexFactory.apply(sourcesDir, key);
        }
    }
}
