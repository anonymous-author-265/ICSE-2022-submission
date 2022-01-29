package edu.utdallas.seers.lasso.experiment;

import com.google.common.collect.ImmutableMap;
import com.opencsv.bean.CsvBindByName;
import edu.utdallas.seers.file.csv.CSVWriter;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.lasso.retrieval.*;
import edu.utdallas.seers.retrieval.AggregatedRetrievalEvaluation;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.lambda.Seq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utdallas.seers.collection.Collections.streamMap;
import static edu.utdallas.seers.lasso.experiment.ConstraintTracingEvaluator.parseArguments;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class LassoWeightEvaluator {
    private final Logger logger = LoggerFactory.getLogger(LassoWeightEvaluator.class);
    private final List<LassoScore.Component> lassoComponents = Arrays.asList(
            LassoScore.Component.CONSTRAINT_OPERAND, LassoScore.Component.ESC_OPERAND,
            LassoScore.Component.EXPECTED_CIP, LassoScore.Component.CONTEXT_METHOD,
            LassoScore.Component.OP_BLOCK
    );
    /**
     * Managed as integers for precision, but will be converted to float.
     * TODO test negative weights
     */
    private final List<Integer> weightValues = Seq.rangeClosed(0, 10).toList();
    private final List<Integer> windowValues = Collections.singletonList(1);
    private final IndexCoordinator coordinator = new IndexCoordinator();

    private final Path constraintsFile;
    private final Path sourcesDir;
    private final Path outputPath;

    public LassoWeightEvaluator(Path constraintsFile, Path sourcesDir, Path outputPath) {
        this.constraintsFile = constraintsFile;
        this.sourcesDir = sourcesDir;
        this.outputPath = outputPath;
    }

    public static void main(String[] args) throws IOException {
        Namespace namespace = parseArguments(args);
        if (namespace == null) return;

        var constraintsFile = Paths.get(namespace.getString("constraints_file"));
        var sourcesDir = Paths.get(namespace.getString("sources_dir"));
        var outputPath = Paths.get(namespace.getString("output_path"));

        new LassoWeightEvaluator(constraintsFile, sourcesDir, outputPath)
                .startExperiment();
    }

    private void startExperiment() throws IOException {
        var outputFile = this.outputPath.resolve("results.csv");
        try (var writer = CSVWriter.<Evaluation>create(outputFile)) {
            evaluateWeights(writer);
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void evaluateWeights(CSVWriter<Evaluation> writer) {
//        var initialWeight = ImmutableMap.<Score.Component, Integer>builder()
//                .put(Score.Component.CONSTRAINT_OPERAND, 10)
//                .build();
        var initialWeight = new HashMap<LassoScore.Component, Integer>();
        var lockedScenario = new Scenario(initialWeight, null, -1);
        var constraints = new ConstraintLoader().loadGrouped(constraintsFile)
                .toMap();

        for (int i = 0; i < lassoComponents.size() - initialWeight.size(); i++) {
            var testingScenario = lockedScenario;
            var scenarios = Seq.seq(lassoComponents)
                    .filter(c -> !testingScenario.weights.containsKey(c))
                    .flatMap(c -> generateWeights(testingScenario, c))
                    // All zeros makes no sense
                    .filter(s -> !s.weights.values().stream().allMatch(w -> w == 0))
                    .map(Scenario::normalize)
                    .distinct(s -> tuple(s.weights, s.windowSize))
                    .toList();

            int round = i + 1;
            var bestWeight = scenarios.parallelStream()
                    .map(s -> evaluateCombination(s, constraints))
                    .peek(r -> writer.writeRow(new Evaluation(round, r)))
                    .max(Comparator.comparing(e -> e.stats.getPercentHitsAtK().get(20)))
                    .get();

            lockedScenario = new Scenario(bestWeight.scenario.weights, null, bestWeight.scenario.windowSize);

            logger.info("Current best configuration: {}", lockedScenario);
        }

        logger.info("Best configuration: {}", lockedScenario);
    }

    private Seq<Scenario> generateWeights(Scenario lockedScenario, LassoScore.Component component) {
        if (component != LassoScore.Component.CQ_BLOCK) {
            // Will be -1 if value has not been set or component is not being used
            int window = lockedScenario.windowSize;

            return Seq.seq(weightValues)
                    .map(w -> lockedScenario.newWith(component, w, window));
        }

        return Seq.seq(weightValues)
                .flatMap(wt -> windowValues.stream().map(wi -> tuple(wt, wi)))
                .map(p -> lockedScenario.newWith(component, p.v1, p.v2));
    }

    private CombinationResult evaluateCombination(Scenario scenario, Map<String, List<PatternEntry>> data) {
        // So that all don't try to index the same project at the same time initially
        var shuffledData = streamMap(data)
                .shuffle()
                .toList();

        var byProject = shuffledData.parallelStream()
                .map(p -> {
                    var windowSize = scenario.windowSize != -1 ?
                            scenario.windowSize :
                            // Use first value as default if CQ is not being used
                            windowValues.get(0);
                    var key = LassoScenarioID.lassoPattern(
                            p.v1, scenario.getFloatWeights(), BaselineIndexBuilder.Type.BM25);
                    var index = coordinator.createIndex(sourcesDir, key);

                    var constraintEvaluations = p.v2.stream()
                            .map(c -> new LassoEvaluation(index.search(c)))
                            .collect(Collectors.toList());

                    return AggregatedRetrievalEvaluation.create(constraintEvaluations);
                })
                .collect(Collectors.toList());

        return new CombinationResult(scenario, AggregatedRetrievalEvaluation.aggregate(byProject));
    }

    private static class CombinationResult {

        private final Scenario scenario;
        private final AggregatedRetrievalEvaluation stats;

        public CombinationResult(Scenario scenario, AggregatedRetrievalEvaluation stats) {
            this.scenario = scenario;
            this.stats = stats;
        }
    }

    public static class Evaluation {

        @CsvBindByName(column = "00 Scenario")
        private final String scenario;
        @CsvBindByName(column = "02 Round")
        private final int round;
        @CsvBindByName(column = "03 %HITS@20")
        private final float hitsAt20;
        @CsvBindByName(column = "04 Average Rank")
        private final float averageRank;
        @CsvBindByName(column = "05 Average Results")
        private final float averageResults;

        public Evaluation(int round, CombinationResult result) {
            scenario = result.scenario.toString();
            hitsAt20 = result.stats.getPercentHitsAtK().get(20);
            averageRank = result.stats.getAverageRank();
            averageResults = result.stats.getAverageResults();
            this.round = round;
        }
    }

    private class Scenario {

        private final Map<LassoScore.Component, Integer> weights;
        private final LassoScore.Component testedComponent;
        private final int windowSize;

        Scenario(Map<LassoScore.Component, Integer> weights, LassoScore.Component testedComponent, int windowSize) {
            this.weights = ImmutableMap.copyOf(weights);
            this.testedComponent = testedComponent;
            this.windowSize = windowSize;
        }

        public Scenario newWith(LassoScore.Component component, int weight, int windowSize) {
            var newWeights = new HashMap<>(weights);
            newWeights.put(component, weight);
            return new Scenario(newWeights, component, windowSize);
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        public Scenario normalize() {
            int maxWeight = weights.values().stream()
                    .mapToInt(i -> i).max().getAsInt();
            float factor = weightValues.get(weightValues.size() - 1) / (float) maxWeight;
            var newWeights = streamMap(weights)
                    .map(c -> c, i -> Math.round(i * factor))
                    .toMap();

            return new Scenario(newWeights, testedComponent, windowSize);
        }

        @Override
        public String toString() {
            return String.format("%s %s%s",
                    LassoScore.repr(getFloatWeights()),
                    windowSize != -1 ? "W:" + windowSize : "",
                    Optional.ofNullable(testedComponent)
                            .map(c -> "/ Testing: " + c)
                            .orElse("")
            );
        }

        private Map<LassoScore.Component, Float> getFloatWeights() {
            return streamMap(weights)
                    .map(c -> c, i -> i / 10f)
                    .toMap();
        }
    }
}
