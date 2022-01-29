package edu.utdallas.seers.lasso.misc;

import edu.utdallas.seers.lasso.ast.TextSpan;
import edu.utdallas.seers.retrieval.Index;
import edu.utdallas.seers.retrieval.IndexBuilder;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.BooleanClause;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class TextSpanIndexBuilder extends IndexBuilder<TextSpan> {

    private final Path cachePath;
    private final TextPreprocessor preprocessor;

    public TextSpanIndexBuilder(Path cachePath, TextPreprocessor preprocessor) {
        this.cachePath = cachePath;
        this.preprocessor = preprocessor;
    }

    @Override
    protected Index<TextSpan> createIndex(Path indexPath) {
        return new FileIndex(indexPath);
    }

    @Override
    protected Optional<Iterable<IndexableField>> generateFields(TextSpan item, String indexName) {
        String text = preprocessor.preprocess(item.getText(), true)
                .collect(Collectors.joining(" "));

        return Optional.of(Arrays.asList(
                new StringField("id", "", Field.Store.YES),
                new Field("type", item.getType().toString(), StringField.TYPE_STORED),
                new Field("location", item.getLocation().toString(), StringField.TYPE_STORED),
                new Field("path", item.getLocation().getFile(), StringField.TYPE_STORED),
                new StringField("line", String.valueOf(item.getLine()), Field.Store.YES),
                new TextField("content", text, Field.Store.YES)
        ));
    }

    @Override
    protected Path resolveIndexPathForName(String indexName) {
        return cachePath.resolve("term-indexes").resolve(indexName);
    }

    private static class FileIndex extends Index<TextSpan> {
        public FileIndex(Path indexPath) {
            super(indexPath);
        }

        @Override
        protected BooleanClause.Occur getOccurClause() {
            return BooleanClause.Occur.MUST;
        }

        @Override
        protected TextSpan loadEntity(ScoredDocID sd) {
            Document doc;
            try {
                doc = searcher.doc(sd.getIndexID());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return TextSpan.createBrief(doc.get("type"), doc.get("location"), Integer.parseInt(doc.get("line")));
        }

        @Override
        protected String getIDFieldName() {
            return "id";
        }
    }
}
