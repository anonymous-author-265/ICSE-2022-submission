package edu.utdallas.seers.lasso.retrieval;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithCondition;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.utdallas.seers.json.AdapterSupplier;
import edu.utdallas.seers.json.JSON;
import edu.utdallas.seers.json.JSONSerializable;
import edu.utdallas.seers.lasso.ast.ASTPatternDetector;
import edu.utdallas.seers.lasso.ast.JavaTextExtractor;
import edu.utdallas.seers.lasso.ast.TextSpan;
import edu.utdallas.seers.lasso.ast.matcher.PatternInstance;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.parameter.Options;
import edu.utdallas.seers.text.preprocessing.Preprocessing;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.file.Files.createDirectories;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class LassoIndexBuilder {
    static final String METHOD_NAME_FIELD_NAME = "methodName";
    static final String CLASS_NAME_FIELD_NAME = "className";
    static final String OPERAND_FIELD_NAME = "OPERAND_";
    static final int MAX_OPERANDS = 2;
    static final String BLOCK_FIELD_NAME = "window";

    private final Logger logger = LoggerFactory.getLogger(LassoIndexBuilder.class);
    private final TextPreprocessor preprocessor = createPreprocessor();
    protected final JavaTextExtractor extractor = new JavaTextExtractor();
    private final LoadingCache<Path, SpanIndex> indexCache = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build(new Loader(new JavaTextExtractor()));

    private final Path dataDir;
    private final String name;
    private final Path cachePath;
    private final Map<String, ASTPattern> patterns = new HashMap<>();
    private final LassoScenarioID<LassoConfig> scenarioKey;
    private final Map<String, LassoStats> stats = new HashMap<>();
    private final IndexCoordinator coordinator;

    public LassoIndexBuilder(Path dataDir, LassoScenarioID<LassoConfig> key, IndexCoordinator coordinator) {
        this.dataDir = dataDir;
        name = createIndexName(key);
        scenarioKey = key;
        this.cachePath = Options.getInstance().getCachePath();
        this.coordinator = coordinator;
    }

    public static String createIndexName(LassoScenarioID<LassoConfig> key) {
        return String.format("%s", key.project);
    }

    /**
     * Preprocessor for Lasso-related functionality.
     *
     * @return Preprocessor.
     */
    public static TextPreprocessor createPreprocessor() {
        return new TextPreprocessor.Builder()
                .setFilterList(Preprocessing.loadStandardStopWords())
                .setMinimumLength(1)
                .build();
    }

    public LassoIndex createIndex() {
        var indexesPath = cachePath.resolve("pattern-indexes");
        var lucenePath = indexesPath.resolve(name);
        var jsonCachePath = indexesPath.resolve(name + "-stats.json");
        createDirectories(lucenePath);

        BaselineIndex baselineIndex;
        var conf = scenarioKey.getConfiguration();
        if (conf.baselineCombination || conf.baselineBoost ||
                LassoIndex.BASELINE_ORDER) {
            if (conf.underlyingType.equals(BaselineIndexBuilder.Type.BM25)) {
                baselineIndex = coordinator.createBaselineIndex(dataDir, new LassoScenarioID<>(
                        scenarioKey.project,
                        new BaselineConfig(BaselineIndexBuilder.Type.BM25,
                                BaselineIndexBuilder.Input.CONTEXT, BaselineIndexBuilder.Output.METHOD, -1)
                ));

            } else if (conf.underlyingType.equals(BaselineIndexBuilder.Type.LSI)) {
                baselineIndex = coordinator.createBaselineIndex(dataDir, new LassoScenarioID<>(
                        scenarioKey.project,
                        new BaselineConfig(BaselineIndexBuilder.Type.LSI,
                                BaselineIndexBuilder.Input.OPERANDS, BaselineIndexBuilder.Output.METHOD, 300)
                ));
            } else {
                baselineIndex = coordinator.createBaselineIndex(dataDir, new LassoScenarioID<>(
                        scenarioKey.project,
                        new BaselineConfig(BaselineIndexBuilder.Type.TFIDF,
                                BaselineIndexBuilder.Input.CONTEXT, BaselineIndexBuilder.Output.METHOD, -1)
                        // TODO parameterize the underlying technique
                        // For Lasso-LSI
                        //            new BaselineConfig(BaselineIndexBuilder.Type.LSI,
                        //                    BaselineIndexBuilder.Input.OPERANDS, BaselineIndexBuilder.Output.METHOD, 300)
                ));
            }
        } else {
            baselineIndex = null;
        }

        try {
            var dir = FSDirectory.open(lucenePath);
            // TODO re-add if call graph needed
//            logger.info("[{}] Reading call graph", name);
//            var callGraph = new LassoCallGraphBuilder(dataDir.resolve(scenarioKey.project).resolve("binaries"))
//                    .buildGraph();

            if (DirectoryReader.indexExists(dir) && !Options.getInstance().isIgnoreCache()) {
                logger.info("[{}] Using existing index at: {}", name, lucenePath);
                var cache = JSON.readJSON(jsonCachePath, Cache.class);

                patterns.putAll(cache.patterns);
                stats.putAll(cache.stats);
            } else {
                logger.info("[{}] Creating new index at: {}", name, lucenePath);

                var config = new IndexWriterConfig()
                        .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                        .setSimilarity(new SimpleCountSimilarity())
                        .setCommitOnClose(true);
                try (var writer = new IndexWriter(dir, config)) {
                    createDocuments()
                            .forEach(Unchecked.consumer(writer::addDocument));
                }

                // Cache stats
                // TODO cache call graph as well
                JSON.writeJSON(new Cache(patterns, stats), jsonCachePath, false);
            }

            return new LassoIndex(DirectoryReader.open(dir), patterns, stats,
                    baselineIndex, scenarioKey, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected Stream<List<Field>> createDocuments() {
        return ASTPatternDetector.detect(dataDir, scenarioKey.project, this::pathFilter)
                .flatMap(Unchecked.biFunction((ff, ps) -> {
                    var index = indexCache.get(ff);
                    return ps.flatMap(pi -> extractText(index, pi)
                                    .map(ts -> {
                                        var id = pi.match.getID();
                                        if (patterns.containsKey(id)) {
                                            logger.warn("Duplicated pattern: " + id);
                                            return null;
                                        }

                                        patterns.put(id, pi.match);
                                        var operandSizes = Seq.seq(ts)
                                                .filter(tt -> tt.v2.name().startsWith(OPERAND_FIELD_NAME))
                                                .map(tt -> tt.map((s, f) ->
                                                        tuple(
                                                                Integer.parseInt(f.name().replace(OPERAND_FIELD_NAME, "")),
                                                                s
                                                        )
                                                ))
                                                .toMap(Tuple2::v1, Tuple2::v2);
//                                var methodTextIndex = extractMethodText(pi.match, index);
                                        stats.put(id, new LassoStats(operandSizes, Collections.emptyMap(), Collections.emptyList()));
                                        return ts.stream()
                                                .map(Tuple2::v2)
                                                .collect(Collectors.toList());
                                    })
                                    .stream()
                    );
                }));
    }

    protected List<Statement> findBlockForPattern(Node currentNode, Node previousNode) {
        if (currentNode instanceof BodyDeclaration) return Collections.emptyList();

        if (currentNode instanceof Statement) {
            // TODO switch?
            if (!(currentNode instanceof NodeWithCondition)) {
                return Collections.emptyList();
            }

            if (previousNode == null) {
                if (!(currentNode instanceof IfStmt)) throw new IllegalArgumentException();
                return currentNode.accept(new BodyExtractor(), null);
            } else {
                // TODO none of the ones in the data are in FOR but it's possible
                if (((NodeWithCondition<?>) currentNode).getCondition() == previousNode) {
                    return currentNode.accept(new BodyExtractor(), null);
                } else {
                    return Collections.emptyList();
                }
            }
        }

        return findBlockForPattern(currentNode.getParentNode().orElseThrow(), currentNode);
    }

    protected boolean pathFilter(Path path) {
        return true;
    }

    private Map<String, List<Integer>> extractMethodText(ASTPattern pattern, SpanIndex index) {
        return index.findMethodSpans(pattern.getLines()).stream()
                .flatMap(s -> {
                    var line = s.docComment ? -1 : s.getLine();
                    return preprocessor.preprocess(s.getText(), true)
                            .map(w -> tuple(w, line));
                })
                .collect(Collectors.toMap(
                        Tuple2::v1,
                        t -> Collections.singletonList(t.v2),
                        (l1, l2) -> Stream.of(l1, l2).flatMap(Collection::stream).collect(Collectors.toList())
                ));
    }

    protected Optional<List<Tuple2<Integer, Field>>> extractText(SpanIndex index, PatternInstance instance) {
        var pattern = instance.match;
        // TODO span index now only exits to extract method and class name, this should instead be done by the detector
        var patternSpans = index.findPatternSpans(pattern.getLines());

        if (patternSpans.isEmpty()) {
            return Optional.empty();
        }

        // TODO pattern should know its location
        var patternLocation = patternSpans.get(0).getLocation();
        var methodNameText = preprocessor.preprocess(patternLocation.getMethodName().orElse(""), true)
                .collect(Collectors.joining(" "));
        var classNameText = preprocessor.preprocess(patternLocation.getClassName().orElse(""), true)
                .collect(Collectors.joining(" "));

        // Block texts
        var blockRawText = findBlockForPattern(instance.matchedNode, null).stream()
                .flatMap(extractor::extractFromNode)
                .map(TextSpan::getText)
                .collect(Collectors.joining(" "));
        var blockText = preprocessor.preprocess(blockRawText, true)
                .collect(Collectors.joining(" "));

        var operandFields = Seq.zipWithIndex(pattern.getOperands())
                .map(t -> t.map((o, i) -> tuple(
                        preprocessor.preprocess(o.getAllText(), true)
                                .collect(Collectors.toList()),
                        i + 1
                )))
                .filter(t -> !t.v1.isEmpty())
                .<Tuple2<Integer, Field>>map(t -> tuple(
                        new HashSet<>(t.v1).size(),
                        new TextField(OPERAND_FIELD_NAME + t.v2, String.join(" ", t.v1), Field.Store.NO)
                ))
                .toList();

        if (operandFields.isEmpty()) return Optional.empty();
        // TODO instead merge additional operands into the second one
        if (operandFields.size() > MAX_OPERANDS)
            throw new RuntimeException(String.format("Pattern has %d operands but at most %d are allowed: %s",
                    operandFields.size(),
                    MAX_OPERANDS,
                    pattern.getID()));

        var simpleFields = Stream.of(
                new StringField("id", pattern.getID(), Field.Store.YES),
                new TextField(METHOD_NAME_FIELD_NAME, methodNameText, Field.Store.NO),
                new TextField(CLASS_NAME_FIELD_NAME, classNameText, Field.Store.NO),
                new TextField(BLOCK_FIELD_NAME, blockText, Field.Store.NO)
        )
                .map(f -> tuple(0, f));

        return Optional.of(
                Stream.concat(simpleFields, operandFields.stream()).collect(Collectors.toList())
        );
    }

    /**
     * To simplify JSON serialization.
     */
    public static class Cache implements JSONSerializable<AdapterSupplier> {

        private final Map<String, ASTPattern> patterns;
        private final Map<String, LassoStats> stats;

        public Cache(Map<String, ASTPattern> patterns, Map<String, LassoStats> stats) {
            this.patterns = patterns;
            this.stats = stats;
        }
    }

    protected static class SpanIndex {

        private final Map<Integer, List<TextSpan>> lineIndex;
        private final Map<TextSpan.Location, List<TextSpan>> locationIndex;

        public SpanIndex(Stream<TextSpan> spans) {
            lineIndex = spans.collect(Collectors.groupingBy(TextSpan::getLine));
            locationIndex = lineIndex.values().stream()
                    .flatMap(Collection::stream)
                    // FIXME this could be a problem for overloads: they all have same method name
                    .collect(Collectors.groupingBy(TextSpan::getLocation));
        }

        public List<TextSpan> findPatternSpans(Set<Integer> lines) {
            return lines.stream()
                    .flatMap(n -> lineIndex.getOrDefault(n, Collections.emptyList()).stream())
                    .collect(Collectors.toList());
        }

        public List<TextSpan> findMethodSpans(Set<Integer> patternLines) {
            var locations = findPatternSpans(patternLines).stream()
                    .map(TextSpan::getLocation)
                    .distinct()
                    .collect(Collectors.toList());
            assert locations.size() == 1;
            return locationIndex.getOrDefault(locations.get(0), Collections.emptyList()).stream()
                    // Exclude the ones that are already in the pattern text
                    .filter(s -> !patternLines.contains(s.getLine()))
                    .collect(Collectors.toList());
        }
    }

    private static class Loader extends CacheLoader<Path, SpanIndex> {

        private final JavaTextExtractor extractor;

        public Loader(JavaTextExtractor extractor) {
            this.extractor = extractor;
        }

        @Override
        public SpanIndex load(Path key) throws IOException {
            return new SpanIndex(extractor.extractText(key));
        }
    }

    private static class BodyExtractor extends GenericVisitorWithDefaults<List<Statement>, Void> {
        @Override
        public List<Statement> visit(DoStmt n, Void arg) {
            return Collections.singletonList(n.getBody());
        }

        @Override
        public List<Statement> visit(IfStmt n, Void arg) {
            return Stream.of(n.getThenStmt(), n.getElseStmt().orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        @Override
        public List<Statement> visit(WhileStmt n, Void arg) {
            return Collections.singletonList(n.getBody());
        }

        @Override
        public List<Statement> defaultAction(Node n, Void arg) {
            throw new IllegalStateException("Invalid node: " + n.getClass());
        }
    }
}
