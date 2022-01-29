package edu.utdallas.seers.lasso.retrieval;

import com.github.javaparser.ast.stmt.Statement;
import com.google.common.collect.Sets;
import edu.utdallas.seers.lasso.ast.JavaTextExtractor;
import edu.utdallas.seers.lasso.ast.TextSpan;
import edu.utdallas.seers.lasso.ast.matcher.PatternInstance;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.lucene.document.Field;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class LassoIndexBuilderTest extends LassoIndexBuilder {

    private static final Logger logger = LoggerFactory.getLogger(LassoIndexBuilderTest.class);

    private final TextPreprocessor preprocessor = LassoIndexBuilder.createPreprocessor();
    private final List<PatternEntry> allConstraints;
    private final Set<String> gtFiles;

    private Map<String, List<Statement>> blocks;
    private List<PatternEntry> foundConstraints;
    private List<Stats> stats;

    public LassoIndexBuilderTest(Path sourcesDir, LassoScenarioID<LassoConfig> key, IndexCoordinator coordinator, List<PatternEntry> constraints) {
        super(sourcesDir, key, coordinator);
        this.allConstraints = new ArrayList<>(constraints);
        gtFiles = constraints.stream()
                .flatMap(c -> c.getGroundTruthIDs().stream().map(s -> s.split(":")[0]))
                .collect(Collectors.toSet());
    }

    public static void main(String[] args) {
        var ss = Path.of("/mnt/storage/Source/fine-grained-traceability/data");
        var coordinator = new IndexCoordinator();
        var stats = new ConstraintLoader()
                .loadGrouped(Path.of("/mnt/storage/Source/fine-grained-traceability/pattern-coding/traced-constraints.csv"))
                .flatMap((p, cs) -> {
                    var key = LassoScenarioID.lassoPattern(p, Collections.emptyMap(), BaselineIndexBuilder.Type.BM25);
                    return new LassoIndexBuilderTest(ss, key, coordinator, cs).count().stream();
                })
                .toList();

        logger.info("ID\tFLD\tSTR\tID\tCOMM\tNUM\tALL");
        stats.forEach(s -> {
            var field = s.textMatches;
//            logger.info("{}\t{}\t{}\t{}\t{}\t{}\t{}",
//                    s.constraint.getID(),
//                    "TEXT",
//                    field.get("STRING"),
//                    field.get("IDENTIFIER"),
//                    field.get("COMMENT"),
//                    field.get("NUMBER"),
//                    field.get("ALL"));
//            field = s.contextMatches;
//            logger.info("{}\t{}\t{}\t{}\t{}\t{}\t{}",
//                    s.constraint.getID(),
//                    "CONTEXT",
//                    field.get("STRING"),
//                    field.get("IDENTIFIER"),
//                    field.get("COMMENT"),
//                    field.get("NUMBER"),
//                    field.get("ALL"));
//            field = s.consequenceMatches;
//            logger.info("{}\t{}\t{}\t{}\t{}\t{}\t{}",
//                    s.constraint.getID(),
//                    "CONSEQUENCE",
//                    field.get("STRING"),
//                    field.get("IDENTIFIER"),
//                    field.get("COMMENT"),
//                    field.get("NUMBER"),
//                    field.get("ALL"));
            field = s.opMatches;
            logger.info("{}\t{}\t{}\t{}\t{}\t{}\t{}",
                    s.constraint.getID(),
                    "OPERAND",
                    field.get("STRING"),
                    field.get("IDENTIFIER"),
                    field.get("COMMENT"),
                    field.get("NUMBER"),
                    field.get("ALL"));
        });
    }

    private List<Stats> count() {
        createIndex();
        return stats;
    }

    private String findGTFile(PatternEntry constraint) {
        var files = constraint.getGroundTruthIDs().stream()
                .map(s -> s.split(":")[0])
                .collect(Collectors.toSet());
        if (files.size() != 1) throw new IllegalArgumentException();

        return files.iterator().next();
    }

    private boolean matchesPattern(PatternEntry constraint, ASTPattern pattern) {
        return constraint.getpType() == pattern.getPatternType() &&
                Arrays.stream(constraint.getpTrus())
                        .anyMatch(t -> Arrays.stream(t.getLines()).anyMatch(pattern.location.getLineNumbers()::contains));
    }

    @Override
    protected boolean pathFilter(Path path) {
        var pathString = path.toString();
        return gtFiles.stream()
                .anyMatch(pathString::endsWith);
    }

    @Override
    public LassoIndex createIndex() {
        this.blocks = new HashMap<>();
        foundConstraints = new ArrayList<>();
        stats = new ArrayList<>();

        createDocuments()
                .forEachOrdered(d -> {
                });

        return null;
    }

    @Override
    protected Optional<List<Tuple2<Integer, Field>>> extractText(SpanIndex index, PatternInstance instance) {
        var fileName = instance.match.getFileName();
        if (!gtFiles.contains(fileName))
            return Optional.empty();

        var matches = allConstraints.stream()
                .filter(c -> findGTFile(c).equals(fileName) && matchesPattern(c, instance.match))
                .collect(Collectors.toList());

        foundConstraints.addAll(matches);
        allConstraints.removeAll(matches);

        if (!matches.isEmpty()) {
            var blocksForConstraint = findBlockForPattern(instance.matchedNode, null);
            if (!blocksForConstraint.isEmpty()) {
                stats.addAll(matches.stream()
                        .map(c -> new Stats(c, blocksForConstraint, preprocessor, extractor))
                        .collect(Collectors.toList())
                );

                blocksForConstraint.forEach(s -> {
                    var texts = extractor.extractFromNode(s).collect(Collectors.toList());
                    System.out.println(texts);
                });
            }
        }

        return Optional.empty();
    }

    private static class Stats {

        private final PatternEntry constraint;
        private final Map<String, Float> textMatches;
        private final TextPreprocessor preprocessor;
        private final Map<String, Float> contextMatches;
        private final Map<String, Float> consequenceMatches;
        private final Map<String, Float> opMatches;

        public Stats(PatternEntry constraint, List<Statement> blocks, TextPreprocessor preprocessor, JavaTextExtractor extractor) {
            this.constraint = constraint;
            this.preprocessor = preprocessor;
            var blockTexts = blocks.stream()
                    .flatMap(extractor::extractFromNode)
                    .collect(Collectors.toList());
            textMatches = calculateMatches(constraint.getText(), blockTexts);
            contextMatches = calculateMatches(constraint.getContext(), blockTexts);
            consequenceMatches = calculateMatches(constraint.consequence, blockTexts);
            opMatches = calculateMatches(String.join(" ", constraint.getOperands()), blockTexts);
        }

        private Map<String, Float> calculateMatches(String field, List<TextSpan> texts) {
            var fieldTerms = preprocessor.preprocess(field, true).collect(Collectors.toSet());

            var byType = Arrays.stream(TextSpan.Type.values())
                    .map(t -> {
                        var textsOfType = texts.stream()
                                .filter(ts -> ts.getType() == t)
                                .map(TextSpan::getText)
                                .collect(Collectors.joining(" "));

                        var typeTerms = preprocessor.preprocess(textsOfType, true)
                                .collect(Collectors.toSet());

                        var pc = Sets.intersection(fieldTerms, typeTerms).size() / (float) fieldTerms.size();
                        return Tuple.tuple(
                                t.toString(),
                                Float.isNaN(pc) ? 0f : pc
                        );
                    })
                    .collect(Collectors.toMap(Tuple2::v1, Tuple2::v2));
            var res = new HashMap<>(byType);

            var allCodeTerms = preprocessor.preprocess(texts.stream()
                    .map(TextSpan::getText)
                    .collect(Collectors.joining(" ")), true)
                    .collect(Collectors.toSet());
            var pc = Sets.intersection(fieldTerms, allCodeTerms).size() / (float) fieldTerms.size();
            res.put("ALL", Float.isNaN(pc) ? 0f : pc);

            return res;
        }
    }
}