package edu.utdallas.seers.lasso.experiment;

import edu.utdallas.seers.file.JavaFileWalker;
import edu.utdallas.seers.file.csv.CSVWriter;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.DetectorInput;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;
import edu.utdallas.seers.lasso.identifier.IdentifierExtractor;
import edu.utdallas.seers.lasso.identifier.IdentifierIndex;
import edu.utdallas.seers.lasso.identifier.IdentifierIndexBuilder;
import edu.utdallas.seers.logging.LogPrefixAdder;
import edu.utdallas.seers.parameter.Options;
import edu.utdallas.seers.retrieval.SimpleRetrievalResult;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.lambda.Unchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IdentifierFinderEvaluator {
    private final Logger logger = LoggerFactory.getLogger(IdentifierFinderEvaluator.class);
    private final Pattern expressionSplitter = Pattern.compile(" +\\W*");
    private final TextPreprocessor textPreprocessor = TextPreprocessor.withStandardStopWords();

    private final Path constraintsFile;
    private final Path sourcesDirectory;
    private final Path outputPath;
    private final boolean filter;
    private final String experimentName;


    public IdentifierFinderEvaluator(Path constraintsFile, Path sourcesDirectory, Path outputPath, String experimentName, boolean filter) {
        this.constraintsFile = constraintsFile;
        this.sourcesDirectory = sourcesDirectory;
        this.outputPath = outputPath;
        this.filter = filter;
        this.experimentName = experimentName;
    }

    public static void main(String[] args) throws IOException {
        var parser = new Options.ArgumentBuilder(IdentifierFinderEvaluator.class.getName())
                .addCachePathOption()
                .addIgnoreCacheOption()
                .build();

        parser.addArgument("constraints_file");
        parser.addArgument("sources_directory");
        parser.addArgument("output_path");

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            return;
        }

        Path constraintsFile = Paths.get(namespace.getString("constraints_file"));
        Path sourcesDirectory = Paths.get(namespace.getString("sources_directory"));
        Path outputPath = Paths.get(namespace.getString("output_path"));

        new IdentifierFinderEvaluator(constraintsFile, sourcesDirectory, outputPath, "all-identifiers", false).evaluate();
        new IdentifierFinderEvaluator(constraintsFile, sourcesDirectory, outputPath, "no-local-no-param", true).evaluate();
    }

    private void evaluate() throws IOException {
        logger.info("Starting identifier finder evaluation");

        String fileName;
        if (filter) {
            fileName = "no-local-no-param.csv";
        } else {
            fileName = "all-identifiers.csv";
        }

        Files.createDirectories(outputPath);

        try (CSVWriter<EvaluationResult> writer = CSVWriter.tryCreate(outputPath.resolve(fileName))) {
            writer.writeRows(new ConstraintLoader()
                    .loadGrouped(constraintsFile)
                    .sorted()
                    .flatMap(this::evaluateProject));
        }

    }

    private Stream<EvaluationResult> evaluateProject(String projectName, List<PatternEntry> constraints) {
        LogPrefixAdder.setPrefix(String.format("[%s] ", projectName));

        logger.info("Evaluating {} constraints", constraints.size());

        Path projectSourcesDirectory = sourcesDirectory.resolve(projectName).resolve("sources");
        Set<Path> exclusions = ConstraintLoader.loadExclusions(projectSourcesDirectory);

        var extractor = new IdentifierExtractor(filter);
        var identifiers = JavaFileWalker.walk(projectSourcesDirectory, exclusions)
                .flatMap(Unchecked.function(extractor::visit))
                .collect(Collectors.toList());

        var corpusIdentifiers = identifiers.stream()
                .map(Attribute::getName)
                .collect(Collectors.toSet());

        IdentifierIndex index = (IdentifierIndex) new IdentifierIndexBuilder(experimentName)
                .buildIndex(projectName, identifiers.stream());

        logger.info("Extracted {} identifiers", corpusIdentifiers.size());

        return constraints.stream()
                .flatMap(c -> evaluateConstraint(projectName, c, corpusIdentifiers, index));
    }

    private Stream<EvaluationResult> evaluateConstraint(String projectName,
                                                        PatternEntry constraint,
                                                        Set<String> corpusIdentifiers,
                                                        IdentifierIndex index) {
        List<String> queryTerms = textPreprocessor.preprocess(constraint.getContext(), true)
                .collect(Collectors.toList());

        return constraint.getInputs().stream()
                .map(DetectorInput::getIdentifier)
                .flatMap(s -> Arrays.stream(expressionSplitter.split(s)))
                .map(s -> {
                    int start = 0;
                    if (!s.isEmpty() && s.charAt(0) == '!') {
                        start = 1;
                    }

                    int end = s.length();
                    int atIndex = s.lastIndexOf('@');
                    if (atIndex > -1) {
                        end = atIndex;
                    }

                    return s.substring(start, end)
                            .replace('$', '.');
                })
                // Filter out literals and operators for now
                .filter(s -> s.contains(".") || s.contains("#"))
                .filter(s -> {
                    boolean valid = corpusIdentifiers.contains(s);
                    if (!valid) {
                        logger.info("Input not found in extracted identifiers: {}", s);
                    }
                    return valid;
                })
                .flatMap(i -> searchIdentifier(queryTerms, i, index, projectName));
    }

    private Stream<EvaluationResult> searchIdentifier(List<String> queryTerms, String groundTruth,
                                                      IdentifierIndex index, String projectName) {
        // Normal query: using the full context text
        EvaluationResult normalEval = evaluateQuery(String.join(" ", queryTerms),
                groundTruth, index, projectName, "normal");

        // Perfect query: only matching terms
        HashSet<String> perfect = new HashSet<>(queryTerms);
        perfect.retainAll(index.findPreprocessedText(groundTruth));

        EvaluationResult perfectEval;
        if (perfect.isEmpty()) {
            perfectEval = new EvaluationResult(projectName, groundTruth, 0, 0, "perfect");
        } else {
            perfectEval = evaluateQuery(String.join(" ", perfect),
                    groundTruth, index, projectName, "perfect");
        }

        return Stream.of(normalEval, perfectEval);
    }

    private EvaluationResult evaluateQuery(String query, String groundTruth, IdentifierIndex index, String projectName, String queryType) {
        var results = index.search(query,
                IdentifierIndexBuilder.TEXT_FIELD_NAME);

        int rank = findRank(groundTruth, results);

        return new EvaluationResult(projectName, groundTruth, rank, results.size(), queryType);
    }

    private Integer findRank(String groundTruth, List<SimpleRetrievalResult<Attribute>> results) {
        return results
                .stream()
                .filter(r -> r.getResult().getName().equals(groundTruth))
                .findFirst()
                .map(SimpleRetrievalResult::getRank)
                .orElse(0);
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    public static class EvaluationResult {

        private final String identifier;
        private final int rank;
        private final float precision;
        private final int results;
        private final String projectName;
        private final String type;

        public EvaluationResult(String projectName, String identifier, int rank, int results, String type) {
            this.projectName = projectName;
            this.identifier = identifier;
            this.rank = rank;
            this.results = results;
            precision = rank > 0 ? 1f / results : 0;
            this.type = type;
        }
    }

}
