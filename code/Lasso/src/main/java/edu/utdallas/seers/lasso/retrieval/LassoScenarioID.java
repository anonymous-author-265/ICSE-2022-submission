package edu.utdallas.seers.lasso.retrieval;

import edu.utdallas.seers.retrieval.ScenarioKey;

import java.util.Map;
import java.util.Objects;

public class LassoScenarioID<C extends RetrievalConfiguration> implements ScenarioKey {

    public static final String TO_STRING_SEPARATOR = "__";

    public final String project;
    private final C configuration;

    public LassoScenarioID(String project, C configuration) {
        this.project = project;
        this.configuration = configuration;
    }

    public static LassoScenarioID<LassoConfig> lassoPattern(String project, Map<LassoScore.Component, Float> weights,
                                                            BaselineIndexBuilder.Type underlyingType) {
        return new LassoScenarioID<>(project, new LassoConfig(
                1,
                false,
                false,
                false,
                true,
                weights,
                1.0f,
                1.0f,
                underlyingType));
    }

    public static LassoScenarioID<LassoConfig> lassoMethod(String project, Map<LassoScore.Component, Float> weights,
                                                           BaselineIndexBuilder.Type underlyingType) {
        return new LassoScenarioID<>(project, new LassoConfig(
                1,
                true,
                false,
                false,
                true,
                weights,
                1.0f,
                1.0f,
                underlyingType));
    }

    public static LassoScenarioID<BaselineConfig> baseline(String project, BaselineIndexBuilder.Type type, BaselineIndexBuilder.Input input, BaselineIndexBuilder.Output output, int dimension) {
        return new LassoScenarioID<>(project, new BaselineConfig(
                type,
                input,
                output,
                dimension));
    }

    public LassoScenarioID<C> removeProject() {
        return new LassoScenarioID<>("", configuration);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LassoScenarioID<?> key = (LassoScenarioID<?>) o;
        return project.equals(key.project) && configuration.equals(key.configuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project, configuration);
    }

    @Override
    public String toString() {
        return project + TO_STRING_SEPARATOR + configuration;
    }

    public C getConfiguration() {
        return configuration;
    }
}
