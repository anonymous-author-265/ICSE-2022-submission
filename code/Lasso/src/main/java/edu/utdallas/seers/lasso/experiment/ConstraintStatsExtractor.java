package edu.utdallas.seers.lasso.experiment;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.exceptions.CsvConstraintViolationException;
import edu.utdallas.seers.file.JavaFileWalker;
import edu.utdallas.seers.file.csv.CSVWriter;
import edu.utdallas.seers.lasso.ast.JavaTextExtractor;
import edu.utdallas.seers.lasso.ast.TextSpan;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.lasso.misc.SourceFile;
import edu.utdallas.seers.lasso.misc.TextSpanIndexBuilder;
import edu.utdallas.seers.lasso.retrieval.LassoIndexBuilder;
import edu.utdallas.seers.retrieval.Index;
import edu.utdallas.seers.retrieval.RetrievalResult;
import edu.utdallas.seers.retrieval.SimpleRetrievalResult;
import edu.utdallas.seers.stream.PairSeq;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class ConstraintStatsExtractor {

    private final Pattern scrDirPattern = Pattern.compile("^.*src/(main/)?(java/)?");

    private final JavaTextExtractor extractor = new JavaTextExtractor();
    private final Path cachePath;
    private final Path constraintsPath;
    private final Path sourcesPath;
    private final Path outputPath;
    /**
     * Preprocessor with empty filter list.
     * FIXME: It is filtering out numbers for being too short
     */
    TextPreprocessor preprocessor = LassoIndexBuilder.createPreprocessor();

    public ConstraintStatsExtractor(Path constraintsPath, Path sourcesPath, Path outputPath, Path cachePath) {
        this.constraintsPath = constraintsPath;
        this.sourcesPath = sourcesPath;
        this.outputPath = outputPath;
        this.cachePath = cachePath;
    }

    public static void main(String[] args) throws IOException {
        var constraintsPath = Paths.get(args[0]);
        var sourcesPath = Paths.get(args[1]);
        var outputPath = Paths.get(args[2]);
        var cachePath = Paths.get(args[3]);

        new ConstraintStatsExtractor(constraintsPath, sourcesPath, outputPath, cachePath)
                .extractStats();
    }

    private void extractStats() throws IOException {
        var results = new ConstraintLoader()
                .loadGrouped(constraintsPath)
                .sorted()
                .flatMap(this::processSystem);

        Files.createDirectories(outputPath);

        List<Result> allResults;
        try (var writer = CSVWriter.<Result>create(outputPath.resolve("individual.csv"))) {
            allResults = results.peek(writer::writeRow)
                    .collect(Collectors.toList());
        }

        try (var writer = CSVWriter.<AggregatedResult>create(outputPath.resolve("summary.csv"))) {
            writer.writeRows(allResults.get(0).ktCombinations.keySet().stream()
                    .map(r -> new AggregatedResult(r, allResults)));
        }
    }

    private Stream<Result> processSystem(String project, List<PatternEntry> constraints) {
        var projectPath = sourcesPath.resolve(project).resolve("sources");

        var sourceFiles = JavaFileWalker.walk(projectPath, ConstraintLoader.loadExclusions(projectPath))
                .map(p -> {
                    var packagePath = scrDirPattern.matcher(projectPath.relativize(p).toString())
                            .replaceFirst("");
                    return new SourceFile(p, packagePath);
                })
                .collect(Collectors.toList());

        var mapping = PairSeq.seq(sourceFiles, SourceFile::getPackagePath, SourceFile::getPath).toMap();
        var sourceFileIndex = new TextSpanIndexBuilder(cachePath, preprocessor)
                .buildIndex(
                        project,
                        sourceFiles.stream()
                                .flatMap(Unchecked.function(s -> extractor.extractText(s.getPath())))
                );

        var groupedConstraints = constraints.stream()
                .collect(Collectors.groupingBy(c -> c.getpTrus()[0].getFile()));

        return streamMap(groupedConstraints)
                .flatMap(Unchecked.biFunction((f, cs) -> {
                    var path = mapping.get(f);
                    var lines = parseFileText(path);

                    return cs.stream()
                            .map(c -> processConstraint(c, lines, sourceFileIndex));
                }));
    }

    private Map<Integer, List<TextSpan>> parseFileText(Path path) throws IOException {
        return extractor.extractText(path)
                .collect(Collectors.groupingBy(TextSpan::getLine));
    }

    private Result processConstraint(PatternEntry constraint, Map<Integer, List<TextSpan>> fileLines, Index<TextSpan> sourceFileIndex) {
        var operands = constraint.getOperands().stream()
                .map(s -> preprocessor.preprocess(s, true).collect(Collectors.joining(" ")))
                .collect(Collectors.toList());

        var gtLines = Arrays.stream(constraint.getpTrus()[0].getLines())
                .boxed()
                .collect(Collectors.toList());
        var groundTruthTerms = gtLines.stream()
                .flatMap(i -> fileLines.getOrDefault(i, Collections.emptyList()).stream()
                        .flatMap(s -> preprocessor.preprocess(s.getText(), true)))
                .collect(Collectors.toList());
        var gtLoc = findGTLocation(fileLines, gtLines);

        var termDistances = calculateTermDistances(operands, gtLines, fileLines);

        var results = operands.stream()
                .map(t -> {
                    if (t.isEmpty())
                        return Collections.<SimpleRetrievalResult<TextSpan>>emptyList();
                    var searchTerm = String.join(" ", t.split("-")).trim();
                    return sourceFileIndex.search(searchTerm, "content");
                })
                .collect(Collectors.toList());
        return new Result(constraint, operands, groundTruthTerms, results,
                termDistances, gtLoc);
    }

    private TextSpan.Location findGTLocation(Map<Integer, List<TextSpan>> fileLines, List<Integer> gtLines) {
        var locations = gtLines.stream()
                .flatMap(i -> fileLines.getOrDefault(i, Collections.emptyList()).stream()
                        .map(TextSpan::getLocation))
                .collect(Collectors.toSet());

        assert locations.size() == 1;

        return locations.iterator().next();
    }

    private List<Integer> calculateTermDistances(List<String> terms, List<Integer> gtLines, Map<Integer, List<TextSpan>> lines) {
        // To compare preserving the sign
        Comparator<Integer> absComparer = Comparator.comparing(Math::abs);
        return terms.stream()
                .map(t -> streamMap(lines)
                        .filter((i, ws) -> !t.isEmpty() && ws.stream()
                                .flatMap(s -> preprocessor.preprocess(s.getText(), true))
                                .anyMatch(w -> w.equals(t)))
                        .combine((i, ws) -> gtLines.stream()
                                .map(j -> i - j)
                                .min(absComparer).orElseThrow())
                        .min(absComparer))
                .flatMap(Optional::stream)
                .sorted(absComparer)
                .collect(Collectors.toList());
    }

    private static class Result {

        @CsvBindByName
        private final String id;
        @CsvBindByName
        private final String system;

        // C stands for "count"
        @CsvBindByName
        private final int c_keyTerms;
        @CsvBindByName
        private final int c_enforcingTerms;

        // O stands for "overlap"
        @CsvBindByName
        private final int o_keyTerm_enforcing;

        // P stands for "percentage"
        @CsvBindByName
        private final float p_keyTerm_enforcing;

        // S stands for "stat"
        @CsvBindByName
        private final String s_closestTerm;

        @CsvBindAndJoinByName(column = "P_KT_.*", elementType = Float.class)
        private final MultiValuedMap<String, Float> ktCombinations = new ArrayListValuedHashMap<>();

        public Result(PatternEntry constraint, List<String> keyTerms, List<String> groundTruthText,
                      List<List<SimpleRetrievalResult<TextSpan>>> searchResults, List<Integer> termDistances, TextSpan.Location gtLoc) {
            this.id = constraint.getID();
            this.system = constraint.getSystem();
            s_closestTerm = termDistances.stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElse("");

            var enforcingBag = Sets.newHashSet(groundTruthText);
            var repo = new KeyTermRepo(gtLoc, keyTerms, enforcingBag, searchResults);

            var byClass = Stream.of(TextSpan.Type.IDENTIFIER, TextSpan.Type.COMMENT,
                    TextSpan.Type.STRING, TextSpan.Type.NUMBER)
                    .collect(Collectors.toMap(t -> t, repo::findForClass));

            var byMethod = Stream.of(TextSpan.Type.IDENTIFIER, TextSpan.Type.COMMENT,
                    TextSpan.Type.STRING, TextSpan.Type.NUMBER)
                    .collect(Collectors.toMap(t -> t, repo::findForMethod));

            Seq.concat(
                    repo.combineSelections(byClass)
                            .map(t -> t.map1(s -> "P_KT_C_" + s)),
                    repo.combineSelections(byMethod)
                            .map(t -> t.map1(s -> "P_KT_M_" + s)))
                    .forEach(t -> ktCombinations.put(t.v1, t.v2));

            this.c_keyTerms = keyTerms.size();
            c_enforcingTerms = enforcingBag.size();
            o_keyTerm_enforcing = repo.getOKTE();
            p_keyTerm_enforcing = o_keyTerm_enforcing / (float) c_keyTerms;
        }
    }

    private static class KeyTermRepo {

        private final Map<String, List<TextSpan>> results;
        private final int o_keyTerm_enforcing;
        private final int keyTermNotInES;
        private final TextSpan.Location gtLoc;

        public KeyTermRepo(TextSpan.Location gtLoc, List<String> keyTerms, Set<String> enforcingBag, List<List<SimpleRetrievalResult<TextSpan>>> searchResults) {
            this.gtLoc = gtLoc;
            var keyTermsInEnforcing = keyTerms.stream()
                    .filter(s -> !s.isEmpty() && enforcingBag.contains(s))
                    .collect(Collectors.toSet());
            o_keyTerm_enforcing = keyTermsInEnforcing.size();

            results = Seq.zip(keyTerms, searchResults)
                    .filter(t -> !t.v1.isEmpty() && !keyTermsInEnforcing.contains(t.v1))
                    .toMap(t -> t.v1, t -> t.v2.stream().map(RetrievalResult::getResult).collect(Collectors.toList()));
            keyTermNotInES = keyTerms.size() - o_keyTerm_enforcing;
        }

        public Seq<Tuple2<String, Float>> combineSelections(Map<TextSpan.Type, Set<String>> selections) {
            return PairSeq.seq(Sets.powerSet(selections.keySet()),
                    ts -> ts.stream()
                            .map(t -> t.toString().substring(0, 1))
                            .sorted()
                            .collect(Collectors.joining("")),
                    ts -> ts.stream()
                            .flatMap(t -> selections.get(t).stream())
                            .collect(Collectors.toSet()))
                    .filter((t, ws) -> !t.isEmpty())
                    .map(t -> t.map2(ts -> (ts.size() + o_keyTerm_enforcing) /
                            ((float) keyTermNotInES + o_keyTerm_enforcing)));
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        private PairSeq<String, List<TextSpan>> findKTGTClass(TextSpan.Type type) {
            var className = gtLoc.getClassName().get();
            return streamMap(results)
                    .filter((w, ss) -> ss.stream()
                            .anyMatch(s -> s.getLocation().getClassName()
                                    .map(n -> n.equals(className))
                                    .orElse(false) &&
                                    s.getType().equals(type)));
        }

        public Set<String> findForClass(TextSpan.Type type) {
            return findKTGTClass(type)
                    .combine((t, ss) -> t)
                    .toSet();
        }

        public Set<String> findForMethod(TextSpan.Type type) {
            var maybeMM = gtLoc.getMethodName();
            if (maybeMM.isEmpty()) return Collections.emptySet();

            var methodName = maybeMM.get();
            return findKTGTClass(type)
                    .filter((w, ss) -> ss.stream()
                            .anyMatch(s -> s.getLocation().getMethodName()
                                    .map(n -> n.equals(methodName)).orElse(false)
                                    && s.getType().equals(type)))
                    .combine((t, ss) -> t)
                    .toSet();
        }

        public int getOKTE() {
            return o_keyTerm_enforcing;
        }
    }

    public static class Counter extends AbstractBeanField<Set<String>, Integer> {

        @Override
        protected String convertToWrite(Object value) {
            return String.valueOf(((Set<?>) value).size());
        }

        @Override
        protected Object convert(String value) throws CsvConstraintViolationException {
            throw new CsvConstraintViolationException("Cannot do this");
        }
    }

    private static class AggregatedResult {

        @CsvBindByName
        private final String combination;
        @CsvBindAndJoinByName(column = "S_.*", elementType = Float.class)
        private final MultiValuedMap<String, Float> stats = new ArrayListValuedHashMap<>();

        public AggregatedResult(String combination, List<Result> results) {
            var values = results.stream()
                    .map(r -> {
                        var rowVal = r.ktCombinations.get(combination);
                        assert rowVal.size() == 1;
                        return rowVal.iterator().next();
                    })
                    .collect(Collectors.toList());

            this.combination = combination;
            Predicate<Float> full = v -> v == 1;
            Predicate<Float> high = v -> v > 0.5 && v < 1;
            Predicate<Float> low = v -> v > 0 && v <= 0.5;
            Predicate<Float> no = v -> v == 0;
            var intervals = ImmutableMap.<String, Predicate<Float>>builder()
                    .put("S_Full", full)
                    .put("S_High", high)
                    .put("S_Low", low)
                    .put("S_No", no)
                    .build();

            // Simple count
            streamMap(intervals)
                    .forEachOrdered((n, p) -> stats.put(n + " Count", (float) Seq.seq(values).count(p)));

            // Percentages
            var resultCount = results.size();
            streamMap(intervals)
                    .forEachOrdered((n, p) -> stats.put(n + " Perc.",
                            (float) Seq.seq(values).count(p) / resultCount));

            // Acc percentages
            Seq.of(tuple("Low", low.or(high).or(full)), tuple("High", high.or(full)))
                    .forEach(t -> stats.put("S_Perc. >= " + t.v1, (float) Seq.seq(values).count(t.v2) / resultCount));
        }
    }
}
