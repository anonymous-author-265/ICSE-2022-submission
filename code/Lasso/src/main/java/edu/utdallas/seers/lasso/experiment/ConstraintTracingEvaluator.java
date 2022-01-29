package edu.utdallas.seers.lasso.experiment;

import com.google.common.collect.ImmutableMap;
import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import edu.utdallas.seers.file.csv.CSVWriter;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.lasso.retrieval.*;
import edu.utdallas.seers.lasso.utils.LineContextExtractor;
import edu.utdallas.seers.parameter.Options;
import edu.utdallas.seers.retrieval.AggregatedRetrievalEvaluation;
import edu.utdallas.seers.retrieval.RetrievalResult;
import net.sourceforge.argparse4j.impl.action.StoreTrueArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class ConstraintTracingEvaluator {

    // TODO parameterize this if necessary
    private static final boolean INDIVIDUAL_OUT_ONLY_GT_RESULT = false;

    private final Logger logger = LoggerFactory.getLogger(ConstraintTracingEvaluator.class);
    private final IndexCoordinator indexManager = new IndexCoordinator();
    private final LineContextExtractor lineExtractor;

    private final Path constraintsFile;
    private final Path sourcesDir;
    private final Path outputPath;
    private final boolean writeIndividual;

    public ConstraintTracingEvaluator(Path constraintsFile, Path sourcesDir, Path outputPath, boolean writeIndividual) {
        this.constraintsFile = constraintsFile;
        this.sourcesDir = sourcesDir;
        this.outputPath = outputPath;
        this.writeIndividual = writeIndividual;
        lineExtractor = new LineContextExtractor(sourcesDir, 6);
    }

    public static void main(String[] args) throws IOException {
        Namespace namespace = parseArguments(args);
        if (namespace == null) return;

        var constraintsFile = Paths.get(namespace.getString("constraints_file"));
        var sourcesDir = Paths.get(namespace.getString("sources_dir"));
        var outputPath = Paths.get(namespace.getString("output_path"));
        var writeIndividual = namespace.<Boolean>get("write_individual");

        new ConstraintTracingEvaluator(constraintsFile, sourcesDir, outputPath, writeIndividual)
                .runExperiment();
    }

    static Namespace parseArguments(String[] args) {
        var parser = new Options.ArgumentBuilder(ConstraintTracingEvaluator.class.getName())
                // Common arguments
                .addIgnoreCacheOption()
                .addCachePathOption()
                .addHitsAtKRanksOption()
                .build();

        parser.addArgument("constraints_file")
                .help("CSV file with constraints");

        parser.addArgument("sources_dir")
                .help("Directory with source code of target systems");

        parser.addArgument("output_path")
                .help("CSV to store evaluation results");

        parser.addArgument("-i", "--write-individual")
                .action(new StoreTrueArgumentAction());

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            return null;
        }

        return namespace;
    }

    private void runExperiment() throws IOException {
        var byConstraintFile = outputPath.resolve("results-all.csv");
        Path byScenarioPath = outputPath.resolve("results-summary.csv");
        Path samplePath = outputPath.resolve("01-sample.csv");
        Path sampleSourcePath = outputPath.resolve("01-sample-source.txt");
        try (var byConstraintWriter = CSVWriter.<ConstraintEvaluation>create(byConstraintFile);
             var byScenarioWriter = CSVWriter.<AggregatedEvaluation>create(byScenarioPath);
             var sampleWriter = CSVWriter.<IndividualResult>create(samplePath);
             var sampleSourceWriter = new PrintWriter(Files.newBufferedWriter(sampleSourcePath))) {
            var scenarios = new ConstraintLoader().loadGrouped(constraintsFile)
                    .sorted()
                    .flatMap(this::generateScenarios)
                    .toList();

            scenarios.parallelStream()
                    .map(this::evaluateScenario)
                    .peek(Unchecked.consumer(es -> writeIndividual(sampleWriter, es, sampleSourceWriter)))
                    .peek(es -> byConstraintWriter.writeRows(es.stream()
                            .map(ConstraintEvaluation::new)))
                    .map(es -> tuple(
                            ((LassoScenarioID) es.get(0).getScenarioKey()).removeProject(),
                            AggregatedRetrievalEvaluation.create(es)
                    ))
                    .collect(Collectors.groupingBy(Tuple2::v1))
                    .forEach((k, es) -> byScenarioWriter.writeRow(new AggregatedEvaluation(
                            k,
                            AggregatedRetrievalEvaluation.aggregate(es.stream().map(Tuple2::v2).collect(Collectors.toList()))
                    )));
        }
    }

    private Stream<EvaluationScenario> generateScenarios(String project, List<PatternEntry> constraints) {
        var lassoWeights = ImmutableMap.<LassoScore.Component, Float>builder()
                .put(LassoScore.Component.CONTEXT_METHOD, 1.0f)
                .put(LassoScore.Component.CONSTRAINT_OPERAND, 0.7f)
                .put(LassoScore.Component.EXPECTED_CIP, 0.2f)
                .put(LassoScore.Component.OP_BLOCK, 0.2f)
                .put(LassoScore.Component.ESC_OPERAND, 0.2f)
                .build();

        return Stream.of(
                // LASSO
                new EvaluationScenario(LassoScenarioID.lassoMethod(project, lassoWeights, BaselineIndexBuilder.Type.TFIDF), constraints),
                new EvaluationScenario(LassoScenarioID.lassoMethod(project, lassoWeights, BaselineIndexBuilder.Type.BM25), constraints),
                new EvaluationScenario(LassoScenarioID.lassoMethod(project, lassoWeights, BaselineIndexBuilder.Type.LSI), constraints),

                // BASELINES
                new EvaluationScenario(LassoScenarioID.baseline(project, BaselineIndexBuilder.Type.TFIDF, BaselineIndexBuilder.Input.CONTEXT, BaselineIndexBuilder.Output.METHOD, -1), constraints),
                new EvaluationScenario(LassoScenarioID.baseline(project, BaselineIndexBuilder.Type.BM25, BaselineIndexBuilder.Input.CONTEXT, BaselineIndexBuilder.Output.METHOD, -1), constraints),
                new EvaluationScenario(LassoScenarioID.baseline(project, BaselineIndexBuilder.Type.LSI, BaselineIndexBuilder.Input.OPERANDS, BaselineIndexBuilder.Output.METHOD, 300), constraints)
        );
    }

    @SuppressWarnings("unchecked")
    private List<LassoEvaluation> evaluateScenario(EvaluationScenario scenario) {
        var key = scenario.key;
        logger.info("Processing scenario: {}", key);

        Function<PatternEntry, LassoEvaluation> action;

        if (key.getConfiguration() instanceof BaselineConfig) {
            var index = indexManager.createBaselineIndex(sourcesDir, (LassoScenarioID<BaselineConfig>) key);
            action = c -> new LassoEvaluation(index.search(c));
        } else {
            var index = indexManager.createIndex(sourcesDir, (LassoScenarioID<LassoConfig>) key);
            action = c -> new LassoEvaluation(index.search(c));
        }
        return scenario.constraints.stream()
                .map(action)
                .collect(Collectors.toList());
    }

    private void writeIndividual(CSVWriter<IndividualResult> writer, List<LassoEvaluation> evaluations, PrintWriter sampleSourceWriter) {
        if (!writeIndividual) {
            return;
        }

        for (LassoEvaluation evaluation : evaluations) {
            var key = (LassoScenarioID) evaluations.get(0).getScenarioKey();
            var project = key.project;
            var gtRank = evaluation.getRank().orElse(-1);
            var constraintID = evaluation.getQuery().getID();

            var gt = evaluation.getRank().map(r -> evaluation.getResults().getCluster(r)).stream();

            Stream<LassoResultCollection.ResultGroup> results;
            if (INDIVIDUAL_OUT_ONLY_GT_RESULT) {
                results = gt;
            } else {
                var topN = evaluation.getResults().clusteredItems().limit(10);
                results = Stream.concat(topN, gt);
            }
            writer.writeRows(results
                    // In case the GT is in top n
                    .distinct()
                    .sorted(Comparator.comparing(r -> r.rank))
                    .peek(g -> {
                                // TODO groups currently contain only a single constraint
                                var result = g.results.get(0);
                                List<LassoResult> patterns;
                                if (!result.getStats().groupedResults.isEmpty()) {
                                    patterns = result.getStats().groupedResults;
                                } else {
                                    patterns = Collections.singletonList(result);
                                }
                                lineExtractor.extractFor(
                                        project,
                                        String.format("%s_%sR%d", key.getConfiguration().toString(), constraintID, g.rank),
                                        patterns.stream()
                                                .map(RetrievalResult::getResult)
                                                .collect(Collectors.toList())
                                )
                                        .forEachOrdered(sampleSourceWriter::println);
                            }
                    )
                    .map(r -> new IndividualResult(r, project, gtRank, constraintID, key))
            );
        }
    }

    private static class AggregatedEvaluation {
        @CsvBindByName(column = "00 Technique")
        private final String scenario;
        @CsvBindByName(column = "Queries")
        private final int queries;
        @CsvBindByName(column = "Average Recall")
        private final float averageRecall;
        @CsvBindByName
        private final float mrr;
        @CsvBindByName
        private final float map;
        @CsvBindByName(column = "Average Results")
        private final float averageResults;
        @CsvBindByName(column = "Average Rank (Over retrieved only)")
        private final float averageRank;
        @CsvBindAndJoinByName(column = "\\d+ %HIT.*", elementType = Float.class)
        private final MultiValuedMap<String, Float> pHits = new ArrayListValuedHashMap<>();
        @CsvBindAndJoinByName(column = "\\d+ HIT.*", elementType = Integer.class)
        private final MultiValuedMap<String, Integer> hits = new ArrayListValuedHashMap<>();

        public AggregatedEvaluation(LassoScenarioID key, AggregatedRetrievalEvaluation evaluation) {
            scenario = key.getConfiguration().toString();
            queries = evaluation.getQueryCount();
            averageRecall = evaluation.getAverageRecall();
            mrr = evaluation.getMrr();
            map = evaluation.getMap();
            averageResults = evaluation.getAverageResults();
            averageRank = evaluation.getAverageRank();
            evaluation.getPercentHitsAtK().forEach((k, p) ->
                    pHits.put(String.format("%02d %%HIT@%02d", Options.getInstance().getHitsAtKRanks().indexOf(k) + 1, k), p));
            evaluation.getHitsAtK().forEach((k, h) ->
                    hits.put(String.format("%02d HIT@%02d", Options.getInstance().getHitsAtKRanks().indexOf(k) + 1, k), h));
        }
    }

    static class ConstraintEvaluation {
        // IDs and info
        @CsvBindByName(column = "00 Technique")
        private final String configuration;
        @CsvBindByName(column = "01 Project")
        private final String project;
        @CsvBindByName(column = "02 ID")
        private final String queryID;
        @CsvBindByName(column = "zy Data Set")
        private final String extra;

        // Retrieval stats
        @CsvBindByName(column = "Rank")
        private final Integer rank;
        @CsvBindByName(column = "Total Results")
        private final int totalResults;
        @CsvBindByName(column = "Precision")
        private final float precision;
        @CsvBindByName(column = "Recall")
        private final float recall;
        @CsvBindAndJoinByName(column = "HIT@.*", elementType = Integer.class)
        private final MultiValuedMap<String, Integer> hits = new ArrayListValuedHashMap<>();

        // Other stats
        @CsvBindByName(column = "Method group: rank")
        private final String rankInMethod;
        @CsvBindByName(column = "Method group: size")
        private final String gtGroupSize;
        @CsvBindByName(column = "Method group: ESC ranks")
        private final String gtESCRanks;

        public ConstraintEvaluation(LassoEvaluation evaluation) {
            var key = (LassoScenarioID) evaluation.getScenarioKey();
            var query = evaluation.getQuery();
            configuration = key.getConfiguration().toString();
            project = key.project;
            queryID = query.getID();
            extra = evaluation.getQuery().getExtra().orElse(null);
            var rank = evaluation.getRank();
            this.rank = rank
                    .orElse(null);
            totalResults = evaluation.getTotalResults();
            precision = evaluation.getPrecision();
            recall = evaluation.getRecall();
            for (int k : Options.getInstance().getHitsAtKRanks()) {
                hits.put(String.format("HIT@%02d?", k), rank.filter(r -> r <= k).map(r -> 1).orElse(0));
            }

            rankInMethod = evaluation.gtMethodRank != null ?
                    String.valueOf(evaluation.gtMethodRank) :
                    "N/A";
            gtGroupSize = evaluation.gtGroupSize != -1 ?
                    String.valueOf(evaluation.gtGroupSize) :
                    "N/A";
            gtESCRanks = evaluation.gtMethodESCRanks.stream().map(Objects::toString).collect(Collectors.joining(", "));
        }
    }

    private static class EvaluationScenario {

        public final LassoScenarioID<?> key;
        public final List<PatternEntry> constraints;

        private EvaluationScenario(LassoScenarioID<?> key, List<PatternEntry> constraints) {
            this.key = key;
            this.constraints = constraints;
        }
    }

    public static class IndividualResult {

        @CsvBindByName
        private final String id;
        @CsvBindByName
        private final int rank;
        @CsvBindByName
        private final float score;
        @CsvBindByName
        private final String termLocations;
        @CsvBindByName
        private final String project;
        @CsvBindByName(column = "Is GT?")
        private final boolean isGT;
        @CsvBindByName
        private final String constraint;
        @CsvBindByName
        private final String scoreComps;
        @CsvBindByName
        private final String groupedResults;

        public IndividualResult(LassoResultCollection.ResultGroup cluster, String project, int gtRank, String constraintID, LassoScenarioID key) {
            var result = cluster.results.get(0);
            constraint = constraintID;
            id = result.getResult().getID();
            this.project = project;
            this.rank = cluster.rank;
            score = result.getScore();
            isGT = result.getRank() == gtRank;
            scoreComps = result.getDecomposedScore().repr();
            termLocations = streamMap(result.getStats().qtLocations)
                    .filter((t, ls) -> !ls.isEmpty())
                    .combine((t, ls) -> t + ":" + ls.stream().map(Object::toString).collect(Collectors.joining(", ")))
                    .collect(Collectors.joining("\n"));
            var group = result.getStats().groupedResults;
            var methodGranularity = Optional.of(key.getConfiguration()).filter(c -> c instanceof LassoConfig)
                    .map(c -> (LassoConfig) c)
                    .map(c -> c.methodGranularity)
                    .orElse(false);
            if (LassoIndex.BASELINE_ORDER || methodGranularity) {
                groupedResults = Optional.of(group)
                        .filter(g -> !g.isEmpty())
                        .map(g -> g.stream()
                                .map(r -> String.format("%s:%d,%d - %s",
                                        r.getResult().getPatternType(),
                                        r.getResult().location.range.begin.line,
                                        r.getResult().location.range.begin.column,
                                        r.getDecomposedScore().repr()))
                                .collect(Collectors.joining("\n")))
                        .orElse("");
            } else {
                this.groupedResults = cluster.results.stream()
                        .limit(20)
                        .map(r -> r.getResult().getID())
                        .collect(Collectors.joining("\n"))
                        + (cluster.results.size() > 20 ? "\n..." : "");
            }
        }
    }
}
