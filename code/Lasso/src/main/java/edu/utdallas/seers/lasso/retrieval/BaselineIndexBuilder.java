package edu.utdallas.seers.lasso.retrieval;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import edu.utdallas.seers.file.JavaFileWalker;
import edu.utdallas.seers.lasso.ast.JavaTextExtractor;
import edu.utdallas.seers.lasso.ast.TextSpan;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import edu.utdallas.seers.lasso.data.entity.PatternEntry;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.parameter.Options;
import edu.utdallas.seers.retrieval.IndexBuilder;
import edu.utdallas.seers.retrieval.Retrievable;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.jooq.lambda.Collectable;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;

public abstract class BaselineIndexBuilder extends IndexBuilder<BaselineIndexBuilder.TextBlock> {
    public static final String FILE_FIELD_NAME = "file";
    public static final String CONTENT_FIELD_NAME = "content";
    public static final String BEGIN_FIELD_NAME = "begin";
    public static final String END_FIELD_NAME = "end";

    protected final Path sourcesPath;
    protected final String project;
    protected final LassoScenarioID<BaselineConfig> key;
    private final JavaTextExtractor textExtractor = new JavaTextExtractor();
    private final TextPreprocessor preprocessor = LassoIndexBuilder.createPreprocessor();

    public BaselineIndexBuilder(Path sourcesPath, LassoScenarioID<BaselineConfig> key) {
        this.sourcesPath = sourcesPath;
        this.project = key.project;
        this.key = key;
    }

    public static String createName(LassoScenarioID<BaselineConfig> key) {
        var config = key.getConfiguration();
        return String.format("%s_%s_%s", config.type, key.project, config.output);
    }

    public BaselineIndex buildIndex() {
        var projectPath = sourcesPath.resolve(project).resolve("sources");
        var extractor = key.getConfiguration().output.extractor;
        var items = JavaFileWalker.walk(projectPath, ConstraintLoader.loadExclusions(projectPath))
                .flatMap(Unchecked.function(
                        f -> extractor.apply(textExtractor.extractText(f))
                ));

        return (BaselineIndex) buildIndex(key.getConfiguration().type.nameFactory.apply(key), items);
    }

    protected String preprocessText(String text) {
        return preprocessor.preprocess(text, true)
                .collect(Collectors.joining(" "));
    }

    @Override
    protected Optional<Iterable<IndexableField>> generateFields(TextBlock item, String indexName) {
        return Optional.of(preprocessText(item.text))
                .filter(s -> !s.isEmpty())
                .map(s -> Arrays.asList(
                        new StringField("id", item.getID(), Field.Store.YES),
                        new StringField(FILE_FIELD_NAME, item.fileName, Field.Store.YES),
                        new TextField(CONTENT_FIELD_NAME, s, Field.Store.NO),
                        new StoredField(BEGIN_FIELD_NAME, item.lineBegin),
                        new StoredField(END_FIELD_NAME, item.lineEnd)
                ));
    }

    @Override
    protected Path resolveIndexPathForName(String indexName) {
        return Options.getInstance().getCachePath()
                .resolve("baseline-indexes").resolve(indexName);
    }

    public enum Type {
        TFIDF(TFIDFIndexBuilder::createIndex, BaselineIndexBuilder::createName, "TF-IDF"),
        BM25(BM25IndexBuilder::createIndex, BaselineIndexBuilder::createName, "Lucene"),
        LSI(LSIIndexBuilder::createIndex, LSIIndexBuilder::createName, "LSI");

        public final BiFunction<Path, LassoScenarioID<BaselineConfig>, BaselineIndex> indexFactory;
        public final Function<LassoScenarioID<BaselineConfig>, String> nameFactory;
        public final String prettyName;

        Type(BiFunction<Path, LassoScenarioID<BaselineConfig>, BaselineIndex> indexFactory,
             Function<LassoScenarioID<BaselineConfig>, String> nameFactory, String prettyName) {
            this.indexFactory = indexFactory;
            this.nameFactory = nameFactory;
            this.prettyName = prettyName;
        }
    }

    public enum Input {
        TEXT(PatternEntry::getText),
        OPERANDS(e -> String.join(" ", e.getOperands())),
        CONTEXT(PatternEntry::getContext);

        public final Function<PatternEntry, String> extractor;

        Input(Function<PatternEntry, String> extractor) {
            this.extractor = extractor;
        }
    }

    public enum Output {
        METHOD(ss -> {
            var grouped = ss
                    .filter(s -> {
                        var location = s.getLocation();
                        // TODO invalid ones should be only text in class definition head and stray comments
                        return location.methodRange != null || location.statementRange != null;
                    })
                    // If there is no method range, use statement range
                    .collect(Collectors.groupingBy(s -> {
                        var range = Optional.ofNullable(s.getLocation().methodRange)
                                .or(() -> Optional.ofNullable(s.getLocation().statementRange))
                                .orElseThrow();

                        // Will join statements that are on same line, e.g. enum constants
                        return new Range(
                                new Position(range.begin.line, 1),
                                new Position(range.end.line, 1)
                        );
                    }));

            return makeTextBlocks(grouped);
        }),

        STATEMENT(ss -> {
            var grouped = ss
                    .filter(s -> s.getLocation().statementRange != null)
                    .collect(Collectors.groupingBy(s -> s.getLocation().statementRange));

            return makeTextBlocks(grouped);
        }),

        LINE(ss -> {
            var grouped = Seq.seq(ss)
                    .grouped(TextSpan::getLine)
                    .map(t -> t.map2(Collectable::toList))
                    // If only comments, it is not a valid line
                    .filter(t -> Seq.seq(t.v2).findFirst(s -> s.getType() != TextSpan.Type.COMMENT).isEmpty())
                    .toMap(
                            t -> new Range(new Position(t.v1, 0), new Position(t.v1, 1)),
                            t -> t.v2
                    );

            return makeTextBlocks(grouped);
        });

        public final Function<Stream<TextSpan>, Stream<TextBlock>> extractor;

        Output(Function<Stream<TextSpan>, Stream<TextBlock>> extractor) {
            this.extractor = extractor;
        }

        private static Seq<TextBlock> makeTextBlocks(Map<Range, List<TextSpan>> grouped) {
            return streamMap(grouped)
                    .combine((t, bs) -> {
                        var fileName = bs.get(0).getLocation().getFile();
                        var text = bs.stream()
                                .map(TextSpan::getText)
                                .collect(Collectors.joining(" "));
                        return new TextBlock(fileName, t.begin.line, t.end.line, text);
                    });
        }
    }

    public static class TextBlock implements Retrievable {

        public final String fileName;
        public final int lineBegin;
        public final int lineEnd;
        public final String text;

        public TextBlock(String fileName, int lineBegin, int lineEnd, String text) {
            this.fileName = fileName;
            this.lineBegin = lineBegin;
            this.lineEnd = lineEnd;
            this.text = text;
        }

        public static TextBlock fromIDString(String idString) {
            var matcher = Pattern.compile("([^:]+):(\\d+)-(\\d+)")
                    .matcher(idString);

            if (!matcher.matches()) throw new IllegalArgumentException(idString);

            return new TextBlock(
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    null
            );
        }

        @Override
        public String getID() {
            return String.format("%s:%d-%d", fileName, lineBegin, lineEnd);
        }

        @Override
        public String toString() {
            return "TextBlock{" +
                    "fileName='" + fileName + '\'' +
                    ", lineBegin=" + lineBegin +
                    ", lineEnd=" + lineEnd +
                    '}';
        }
    }

    // FIXME bad inheritance!! Instead we should abstract functionality in PatternResultCollection and related classes
    public static class FakePattern extends ASTPattern {

        private final String type;

        FakePattern(Location location, String type) {
            super(location, PatternType.ASSIGN_CONSTANT, Collections.emptyList());
            this.type = type;
        }

        @Override
        public String getID() {
            return type + ";" + location;
        }
    }
}
