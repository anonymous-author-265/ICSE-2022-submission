package edu.utdallas.seers.lasso.retrieval;

import com.github.javaparser.Range;
import com.google.common.graph.ImmutableGraph;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.ConstraintType;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.retrieval.RetrievalResult;
import edu.utdallas.seers.stream.PairSeq;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;
import static org.jooq.lambda.tuple.Tuple.tuple;

// TODO combine this implementation with IndexSearcher and IndexBuilder in seers.base
public class LassoIndex {
    // TODO these config parameters should be properly parameterized
    public static final boolean BASELINE_ORDER = false;
    public static final boolean AVG_RANKS = false;

    private final Logger logger = LoggerFactory.getLogger(LassoIndex.class);
    private final TextPreprocessor preprocessor = LassoIndexBuilder.createPreprocessor();
    private final QueryRunner runner = new QueryRunner();
    private final Comparator<LassoResult> scoreSorter =
            Comparator.<LassoResult, Float>comparing(RetrievalResult::getScore).reversed();

    private final IndexSearcher indexSearcher;
    private final IndexReader indexReader;
    private final Map<String, ASTPattern> patterns;
    private final Map<String, LassoStats> stats;
    private final Map<LassoScore.Component, Float> scoreWeights;
    private final BaselineIndex baselineIndex;
    private final LassoScenarioID<LassoConfig> key;
    private final ImmutableGraph<String> callGraph;

    public LassoIndex(IndexReader reader, Map<String, ASTPattern> patterns,
                      Map<String, LassoStats> stats,
                      BaselineIndex baselineIndex, LassoScenarioID<LassoConfig> scenarioKey, ImmutableGraph<String> callGraph) {
        this.patterns = patterns;
        indexReader = reader;
        this.stats = stats;
        indexSearcher = new IndexSearcher(indexReader);
        indexSearcher.setSimilarity(new SimpleCountSimilarity());
        this.scoreWeights = scenarioKey.getConfiguration().scoreWeights;
        this.baselineIndex = baselineIndex;
        key = scenarioKey;
        this.callGraph = callGraph;
    }

    public LassoResultCollection search(PatternEntry query) {
        logger.info("[{}] Searching {}", key, query.getID());

        List<List<String>> operandTerms = extractOperandTerms(query);
        var allTerms = operandTerms.stream().flatMap(Collection::stream).collect(Collectors.toList());

        LassoResultCollection baselineResults;
        if (key.getConfiguration().baselineBoost) {
            baselineResults = baselineIndex.search(query);
        } else {
            baselineResults = null;
        }

        var patternResults = buildResultList(query, operandTerms, baselineResults);

        List<LassoResult> finalResults;
        if (BASELINE_ORDER) {
            finalResults = baselineResults(patternResults, query);
        } else if (key.getConfiguration().methodGranularity || key.getConfiguration().allMethods) {
            finalResults = toMethodLevel(patternResults, baselineResults);
        } else {
            finalResults = patternResults;
        }

        return new LassoResultCollection(key, query, allTerms, finalResults, false);
    }

    private List<LassoResult> buildResultList(PatternEntry query, List<List<String>> queryTerms, LassoResultCollection baselineResults) {
        int querySize = (int) queryTerms.stream().flatMap(Collection::stream).distinct().count();
        var consequenceTerms = preprocessor.preprocess(query.consequence, true)
                .collect(Collectors.toList());
        var textTerms = preprocessor.preprocess(query.getText(), true).collect(Collectors.toList());
        var ttUnique = new HashSet<String>(textTerms);

        var queryResults =
                runner.runQueries(queryTerms, consequenceTerms, textTerms, key.getConfiguration().scoreWeights);
        var qOpSizes = Seq.zipWithIndex(queryTerms)
                .map(t -> t.map((ts, i) -> tuple(i.intValue() + 1, ts.size())))
                .toMap(Tuple2::v1, Tuple2::v2);
        var consequenceTermsUnique = new HashSet<>(consequenceTerms);

        // Map: (file, line) --> boost amount
        var baselineBoost = calculateBaselineBoosts(query, baselineResults);

        var sortedResults = queryResults.items()
                .combine(Unchecked.biFunction((i, ss) -> {
                    String indexID = indexReader.document(i, Collections.singleton("id"))
                            .getField("id")
                            .stringValue();

                    var astPattern = patterns.get(indexID);
                    var patternStats = this.stats.get(indexID);

                    var baseScore = score(astPattern, ss, querySize, patternStats,
                            query.getcType(),
                            qOpSizes, consequenceTermsUnique.size(),
                            AVG_RANKS ? Collections.emptyMap() : baselineBoost, ttUnique.size());
                    var summary = new LassoSummary(astPattern, patternStats, consequenceTermsUnique);
                    return new LassoResult(
                            astPattern,
                            0,
                            baseScore,
                            summary);
                }))
                .filter(r -> r.getDecomposedScore().hasCore())
                .sorted(scoreSorter);

        var filteredResults = filterOverlaps(sortedResults);
        var methodLevelProcessedResults = penalizeMethodLevel(filteredResults);
        var scoredResults = penalizeWithCallGraph(methodLevelProcessedResults);

        Seq<LassoResult> combined;
        if (AVG_RANKS) {
            float alpha = 0.9f;
            combined = PairSeq.zipWithIndex(scoredResults)
                    // New "rank" as the average of two ranks is assigned, ties resolved arbitrarily
                    .sortedComparing((r, i) -> {
                        long lassoRank = i + 1;
                        var key = tuple(
                                r.getResult().getFileName(),
                                r.getResult().location.getLineNumbers().iterator().next()
                        );
                        return Optional.ofNullable(baselineBoost.get(key))
                                /* New rank is average of lasso and baseline ranks, if the latter
                                 * exists, otherwise just lasso rank */
                                .map(br -> (alpha * lassoRank) + ((1 - alpha) * br))
                                .orElse((float) lassoRank);
                    })
                    .combine((r, i) -> r);
        } else {
            combined = scoredResults;
        }

        // FIXME convert this to strategy along with other props in scenario key
        Seq<LassoResult> finalResults;
        if (key.getConfiguration().baselineCombination) {
            finalResults = filterWithBaseline(query, combined);
        } else {
            finalResults = combined;
        }

        return PairSeq.zipWithIndex(finalResults)
                .combine((r, i) -> {
                    r.reRank(i.intValue() + 1);
                    return r;
                })
                .collect(Collectors.toList());
    }

    private LassoScore score(ASTPattern pattern, Map<String, Float> queryScores, int operandTermCount, LassoStats patternStats,
                             ConstraintType constraintType, Map<Integer, Integer> qOpSizes,
                             int consequenceSize, Map<Tuple2<String, Integer>, Float> baselineBoost, int textSize) {
        float kt_mn = queryScores.getOrDefault(QueryType.OT_METHOD_NAME.toString(), 0f) / operandTermCount;
        float kt_cn = queryScores.getOrDefault(QueryType.OT_CLASS_NAME.toString(), 0f) / operandTermCount;
        float cq_b = consequenceSize > 0 ?
                queryScores.getOrDefault(QueryType.CQ_BLOCK.toString(), 0f) / consequenceSize :
                0;
        float op_b = queryScores.getOrDefault(QueryType.OP_BLOCK.toString(), 0f) / operandTermCount;
        float txt_b = textSize > 0 ?
                queryScores.getOrDefault(QueryType.TEXT_BLOCK.toString(), 0f) / textSize :
                0f;

        var q_opSS = calculateOperandScore(queryScores, 0, qOpSizes.size(), qOpSizes);
        var c_opSS = calculateOperandScore(queryScores, 1, patternStats.operandCount, patternStats.operandSizes);
        var ix = constraintType.getExpectedPatternTypes().indexOf(pattern.getPatternType());
        float cip_exp = ix == 0 ? 1f : (ix == 1 ? 0.5f : 0f);
        // It is fine to use only one line because all pattern lines will be in the baseline result, if it exists
        var boostKey = tuple(pattern.getFileName(), pattern.location.getLineNumbers().iterator().next());

        var bb = baselineBoost.getOrDefault(boostKey, 0f);
        return new LassoScore.Builder(scoreWeights)
                .addOperandMatchCount(q_opSS.v1, qOpSizes.size(), c_opSS.v1, patternStats.operandCount)
                .addScore(LassoScore.Component.CONSTRAINT_OPERAND, q_opSS.v2)
                .addScore(LassoScore.Component.ESC_OPERAND, c_opSS.v2)
                .addScore(LassoScore.Component.OT_METHOD_NAME, kt_mn)
                .addScore(LassoScore.Component.OT_CLASS_NAME, kt_cn)
                .addScore(LassoScore.Component.EXPECTED_CIP, cip_exp)
                .addScore(LassoScore.Component.CQ_BLOCK, cq_b)
                .addScore(LassoScore.Component.OP_BLOCK, op_b)
                .addScore(LassoScore.Component.CONTEXT_METHOD, bb)
                .build();
    }

    private Seq<LassoResult> penalizeWithCallGraph(Seq<LassoResult> results) {
        if (key.getConfiguration().callGraphPenaltyFactor == 1) {
            return results;
        }

        var resultList = results.toList();
        var methodNameCache = Seq.seq(resultList)
                .filter(r -> r.getResult().location.getMethodName().isPresent())
                .toMap(
                        r -> r.getResult().getID(),
                        r -> {
                            var location = r.getResult().location;
                            return location.getMethodName()
                                    .map(LassoCallGraphBuilder::toCGMethodName)
                                    .map(n -> location.getClassName().orElseThrow() + ":" + n)
                                    .orElseThrow();
                        });
        var methodsWithResults = new HashSet<>(methodNameCache.values());

        var processedResults = resultList.stream()
                .map(r -> {
                    var method = methodNameCache.get(r.getResult().getID());
                    var callersWithResults = Optional.ofNullable(method)
                            .filter(m -> callGraph.nodes().contains(m))
                            .map(m -> callGraph.predecessors(m).stream())
                            .orElse(Stream.empty())
                            .filter(methodsWithResults::contains)
                            .collect(Collectors.toList());

                    if (callersWithResults.isEmpty()) {
                        return r;
                    }

                    float basePenalty = key.getConfiguration().callGraphPenaltyFactor;
                    float penaltyFactor = 1.0f / callersWithResults.size();
                    // Penalty approaches 1.0 (full score) as number of callers increases
                    float penalty = 1 - (penaltyFactor * basePenalty);

                    // The method where the result appears gets called by at least another method with results
                    return new LassoResult(
                            r.getResult(),
                            r.getRank(),
                            r.getDecomposedScore().penalize(penalty),
                            r.getStats()
                    );
                })
                .sorted(scoreSorter)
                .collect(Collectors.toList());

        return Seq.seq(processedResults);
    }

    private Seq<LassoResult> penalizeMethodLevel(List<LassoResult> sortedResults) {
        if (key.getConfiguration().rankPenaltyPercent >= 1) {
            return Seq.seq(sortedResults);
        }

        return groupedByMethod(sortedResults)
                .flatMap((k, rs) -> {
                    int noPenaltyRank = (int) Math.ceil(key.getConfiguration().rankPenaltyPercent * rs.size());
                    var penaltyResults = rs.stream().skip(noPenaltyRank)
                            .map(r -> new LassoResult(
                                    r.getResult(),
                                    r.getRank(),
                                    r.getDecomposedScore().penalize(0.5f),
                                    r.getStats()
                            ));

                    return Seq.concat(
                            rs.stream().limit(noPenaltyRank),
                            penaltyResults
                    );
                })
                .sorted(scoreSorter);
    }

    private List<LassoResult> baselineResults(List<LassoResult> patternResults, PatternEntry query) {
        var grouped = patternResults.stream()
                .collect(Collectors.groupingBy(r -> {
                    var location = r.getResult().location;
                    return tuple(
                            location.packagePath,
                            location.methodRange.begin.line,
                            location.methodRange.end.line
                    );
                }));

        return baselineIndex.search(query).items()
                .map(r -> {
                    var result = r.getResult();
                    var location = result.location;
                    var candidatesInMethod = grouped.getOrDefault(
                            tuple(location.packagePath, location.methodRange.begin.line, location.methodRange.end.line),
                            Collections.emptyList()
                    );
                    var fakeStats = new LassoStats(Collections.emptyMap(), Collections.emptyMap(),
                            candidatesInMethod
                    );
                    return LassoResult.createDummy(location, r.getScore(), r.getRank(),
                            fakeStats, baselineIndex.key.getConfiguration().output);
                })
                .collect(Collectors.toList());
    }

    private List<LassoResult> toMethodLevel(List<LassoResult> patternResults, LassoResultCollection baselineResults) {
        var lassoResults = PairSeq.zipWithIndex(groupedByMethod(patternResults))
                .combine((p, i) -> {
                    var id = p.v1;
                    var methodResults = p.v2;
                    var loc = new ASTPattern.Location(Path.of(id.v1), id.v1, id.v2, id.v2, null, null);
                    var fakeStats = new LassoStats(Collections.emptyMap(), Collections.emptyMap(),
                            methodResults);
                    return LassoResult.createDummy(loc, 1f, i.intValue() + 1, fakeStats,
                            baselineIndex.key.getConfiguration().output);
                })
                .toList();

        if (!key.getConfiguration().allMethods) {
            return lassoResults;
        }

        Function<LassoResult, String> keyBuilder = r -> {
            var location = r.getResult().location;
            return String.format("%s-%d-%d", location.packagePath, location.methodRange.begin.line, location.methodRange.end.line);
        };
        var lassoCache = PairSeq.seq(lassoResults, keyBuilder, r -> r).toMap();
        var baselineCache = PairSeq.seq(baselineResults.items(), keyBuilder, r -> r)
                .collect(Collectors.toMap(
                        Tuple2::v1,
                        Tuple2::v2,
                        // FIXME there should be no duplicates
                        (v1, v2) -> v1
                ));

        var sortedResults = Seq.of(lassoCache.keySet(), baselineCache.keySet()).flatMap(Collection::stream)
                // For all method ranges from both lasso and the baseline
                .distinct()
                .map(k -> {
                    var lassoResult = Optional.ofNullable(lassoCache.get(k));
                    var baselineResult = Optional.ofNullable(baselineCache.get(k));

                    var rankAvg = lassoResult.map(lr -> baselineResult
                            .map(br -> (lr.getRank() + br.getRank()) / 2f)
                            .orElse((float) lr.getRank())
                    )
                            .orElseGet(() -> (float) baselineResult.map(RetrievalResult::getRank).orElseThrow());

                    return tuple(rankAvg, lassoResult.orElseGet(baselineResult::orElseThrow));
                })
                .sorted(Comparator.comparing(Tuple2::v1))
                .map(Tuple2::v2);

        return PairSeq.zipWithIndex(sortedResults)
                .combine((r, i) -> {
                    var fakeStats = new LassoStats(Collections.emptyMap(), Collections.emptyMap(), r.getStats().groupedResults);
                    return LassoResult.createDummy(
                            r.getResult().location,
                            -1,
                            i.intValue() + 1,
                            fakeStats,
                            baselineIndex.key.getConfiguration().output
                    );
                })
                .toList();
    }

    private PairSeq<Tuple2<String, Range>, List<LassoResult>> groupedByMethod(List<LassoResult> patternResults) {
        return PairSeq.grouped(patternResults, r -> {
            var location = r.getResult().location;
            return tuple(location.packagePath, location.methodRange);
        })
                .map(k -> k, rs -> rs.sorted(Comparator.comparing(RetrievalResult::getRank)).toList())
                .sorted(Comparator.comparing(p -> p.v2.get(0).getRank()));
    }

    private List<List<String>> extractOperandTerms(PatternEntry query) {
        var initialOperandTerms = query.getOperands().stream()
                .map(s -> preprocessor.preprocess(s, true).collect(Collectors.toList()))
                .collect(Collectors.toList());

        List<List<String>> finalOperands;
        switch (query.getcType()) {
            case DUAL_VALUE_COMPARISON:
                // TODO we should be using the second operand as well
                if (initialOperandTerms.size() != 2) {
                    logger.warn(String.format("%s should have exactly 2 operands, has %d",
                            query.getID(), initialOperandTerms.size()));
                    finalOperands = initialOperandTerms;
                } else {
                    // Only leave first one
                    finalOperands = initialOperandTerms.subList(0, 1);
                }
                break;
            case CATEGORICAL_VALUE:
                // combine all but first operand into a single one
                // TODO there should be a better way other than combining them
                finalOperands = new ArrayList<>();
                finalOperands.add(initialOperandTerms.get(0));
                finalOperands.add(
                        initialOperandTerms.stream()
                                .skip(1)
                                .flatMap(Collection::stream)
                                .collect(Collectors.toList())
                );
                break;
            default:
                finalOperands = initialOperandTerms;
        }

        return finalOperands;
    }

    private List<LassoResult> filterOverlaps(Seq<LassoResult> sortedResults) {
        // When there are multiple patterns on the same line, we return only the highest ranked for each line
        var linesCovered = new HashSet<Tuple2<String, Integer>>();

        return sortedResults
                .filter(r -> {
                    var location = r.getResult().location;
                    var lines = location.getLineNumbers().stream()
                            .map(i -> tuple(location.packagePath, i))
                            .collect(Collectors.toList());
                    if (!linesCovered.containsAll(lines)) {
                        // This pattern covers at least one new line
                        linesCovered.addAll(lines);
                        return true;
                    }

                    return false;
                })
                // Execute operations here so that we don't carry around mutable linesCovered map
                .toList();
    }

    private Seq<LassoResult> filterWithBaseline(PatternEntry query, Seq<LassoResult> sorted) {
        var methodLines = baselineIndex.search(query).items()
                .flatMap(r -> r.getResult().location.getLineNumbers().stream()
                        .map(i -> tuple(r.getResult().getFileName(), i))
                )
                .collect(Collectors.toSet());

        return sorted
                .filter(r -> methodLines.contains(tuple(
                        r.getResult().getFileName(),
                        // Any line works because all pattern lines will be in the method
                        r.getResult().location.getLineNumbers().iterator().next()
                )));
    }

    private Tuple2<Integer, Float> calculateOperandScore(Map<String, Float> queryScores, int operandIndex, int operandCount, Map<Integer, Integer> operandSizes) {
        // Map from indexed operand to highest score when retrieved with query operand
        var codeOpScores = streamMap(queryScores)
                // Operand scores where there is some match
                .filter((n, s) -> Character.isDigit(n.charAt(0)) && s > 0)
                // Keep scores of indexed fields
                .collect(Collectors.toMap(
                        t -> Integer.parseInt(t.v1.split("_")[operandIndex]),
                        t -> t.v2,
                        // Keep the highest score of all query operands for each indexed operand
                        (s1, s2) -> Optional.of(s1).filter(s -> s > s2).orElse(s2)
                ));

        var finalScore = streamMap(codeOpScores)
                // The operand must have a non-zero size if there was a match
                .combine((n, s) -> s / operandSizes.get(n))
                .sum()
                .map(s -> s / operandCount)
                .orElse(0f);

        return tuple(codeOpScores.size(), finalScore);
    }

    private Map<Tuple2<String, Integer>, Float> calculateBaselineBoosts(PatternEntry query, LassoResultCollection baselineResults) {
        Map<Tuple2<String, Integer>, Float> baselineBoost;
        if (key.getConfiguration().baselineBoost) {
            baselineBoost = PairSeq.zipWithIndex(baselineResults.items())
                    .flatCombine((p, i) -> p.getResult().location.getLineNumbers().stream()
                            .map(ln -> tuple(
                                    tuple(p.getResult().getFileName(), ln),
                                    AVG_RANKS ?
                                            i + 1 :
                                            1.0f / ((float) Math.sqrt(i + 1))
                            ))
                    )
                    .collect(Collectors.toMap(
                            Tuple2::v1,
                            Tuple2::v2,
                            // FIXME there should be no overlap in the baseline results
                            (s1, s2) -> s1 > s2 ? s1 : s2
                    ));
        } else {
            baselineBoost = Collections.emptyMap();
        }

        return baselineBoost;
    }

    enum QueryType {
        //        KT_PAT_TEXT(PatternIndexBuilder.PATTERN_TEXT_FIELD_NAME),
//        KT_DDS_TEXT(PatternIndexBuilder.DATA_DEFINITION_TEXT_FIELD_NAME),
//        KT_METHOD_TEXT(PatternIndexBuilder.METHOD_TEXT_FIELD_NAME),
//        CT_METHOD_TEXT(PatternIndexBuilder.METHOD_TEXT_FIELD_NAME),
        OT_METHOD_NAME(LassoScore.Component.OT_METHOD_NAME, LassoIndexBuilder.METHOD_NAME_FIELD_NAME),
        //        CT_METHOD_NAME(PatternIndexBuilder.METHOD_NAME_FIELD_NAME),
        OT_CLASS_NAME(LassoScore.Component.OT_CLASS_NAME, LassoIndexBuilder.CLASS_NAME_FIELD_NAME),
        CQ_BLOCK(LassoScore.Component.CQ_BLOCK, LassoIndexBuilder.BLOCK_FIELD_NAME),
        OP_BLOCK(LassoScore.Component.OP_BLOCK, LassoIndexBuilder.BLOCK_FIELD_NAME),
        TEXT_BLOCK(LassoScore.Component.TEXT_BLOCK, LassoIndexBuilder.BLOCK_FIELD_NAME)
//        CT_CLASS_NAME(PatternIndexBuilder.CLASS_NAME_FIELD_NAME)
        ;
        private final LassoScore.Component component;
        private final String fieldName;

        QueryType(LassoScore.Component component, String fieldName) {
            this.component = component;
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    static class QueryResults {

        private final Map<Integer, Map<String, Float>> scores;

        public QueryResults(Map<Integer, Map<String, Float>> scores, List<QueryType> queryTypes) {
            this.scores = scores;
        }

        public PairSeq<Integer, Map<String, Float>> items() {
            return streamMap(scores);
        }
    }

    class QueryRunner {
        List<QueryType> simpleQueryTypes = Arrays.asList(QueryType.values());

        public QueryResults runQueries(List<List<String>> operandText, List<String> consequenceTerms,
                                       List<String> textTerms, Map<LassoScore.Component, Float> scoreWeights) {
            // There is one query for each combination of query operand and index operand
            var operandQueries = Seq.zipWithIndex(operandText)
                    .flatMap(t -> t.map((ts, i) ->
                            Seq.rangeClosed(1, LassoIndexBuilder.MAX_OPERANDS)
                                    .map(ip -> tuple((i + 1) + "_" + ip, ts))
                    ));
            var operandTerms = operandText.stream().flatMap(Collection::stream).distinct().collect(Collectors.toList());
            var simpleQueries = simpleQueryTypes.stream()
                    .filter(t -> scoreWeights.getOrDefault(t.component, 0f) > 0)
                    .map(t -> {
                        List<String> terms;
                        if (t.toString().startsWith("CQ")) {
                            terms = consequenceTerms;
                        } else if (t.toString().startsWith("OP") ||
                                t.toString().startsWith("OT")) {
                            terms = operandTerms;
                        } else if (t.toString().startsWith("TEXT")) {
                            terms = textTerms;
                        } else {
                            throw new IllegalArgumentException("Unknown query type");
                        }

                        return tuple(
                                t.toString(),
                                terms
                        );
                    });

            var qs = Seq.concat(operandQueries, simpleQueries);
            var scores = qs.map(t -> t.map(Unchecked.biFunction((n, ts) ->
                    tuple(n, runQuery(n, ts))
            )))
                    .flatMap(t -> t.map((n, ds) ->
                            Arrays.stream(ds.scoreDocs).map(d -> tuple(n, d))
                    ))
                    .grouped(p -> p.v2.doc)
                    .collect(Collectors.toMap(
                            p -> p.v1,
                            p -> p.v2.toMap(t -> t.v1, t -> t.v2.score)
                    ));

            return new QueryResults(scores, simpleQueryTypes);
        }

        private TopDocs runQuery(String queryName, List<String> queryTerms) throws IOException {
            String fieldName;
            if (Arrays.stream(QueryType.values()).map(Objects::toString)
                    .anyMatch(n -> n.equals(queryName))) {
                fieldName = QueryType.valueOf(queryName).getFieldName();
            } else {
                fieldName = LassoIndexBuilder.OPERAND_FIELD_NAME + queryName.split("_")[1];
            }

            var builder = new BooleanQuery.Builder();
            for (String s : queryTerms) {
                builder.add(new TermQuery(new Term(fieldName, s)), BooleanClause.Occur.SHOULD);
            }

            return indexSearcher.search(builder.build(), Integer.MAX_VALUE);
        }
    }
}
