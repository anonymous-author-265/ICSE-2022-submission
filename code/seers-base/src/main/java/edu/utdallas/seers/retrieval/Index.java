package edu.utdallas.seers.retrieval;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class Index<T extends Retrievable> {

    private final Pattern whitespacePattern = Pattern.compile("\\s+");
    protected final IndexSearcher searcher;

    // TODO get rid of constructor exception (factory method)
    protected Index(Path path) {
        IndexReader reader;
        try {
            reader = DirectoryReader.open(FSDirectory.open(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        searcher = new IndexSearcher(reader);
        getSimilarity().ifPresent(searcher::setSimilarity);
    }

    protected Index() {
        searcher = null;
    }

    public List<SimpleRetrievalResult<T>> search(String text, String field) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        whitespacePattern.splitAsStream(text)
                .forEach(t -> builder.add(
                        new TermQuery(new Term(field, t)),
                        getOccurClause()
                ));

        TopDocs topDocs;
        try {
            topDocs = searcher.search(builder.build(), Integer.MAX_VALUE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        AtomicInteger rank = new AtomicInteger(1);

        var results = Arrays.stream(topDocs.scoreDocs)
                .sequential()
                .map(sd -> {
                    Document doc;
                    String idFieldName = getIDFieldName();

                    try {
                        doc = searcher.doc(sd.doc, Collections.singleton(idFieldName));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    // Will throw an exception if null, i.e. field does not exist
                    String docId = Optional.of(doc.get(idFieldName)).get();

                    return new ScoredDocID(sd.doc, docId, rank.getAndIncrement(), sd.score);
                });

        return results
                .map(sd -> new SimpleRetrievalResult<>(loadEntity(sd), sd.getRank(), sd.getScore()))
                .collect(Collectors.toList());
    }

    protected BooleanClause.Occur getOccurClause() {
        return BooleanClause.Occur.SHOULD;
    }

    protected abstract T loadEntity(ScoredDocID sd);

    protected Optional<Similarity> getSimilarity() {
        return Optional.empty();
    }

    /**
     * Returns the name of the field that will be used as ID in the Lucene index.
     *
     * @return Field name.
     */
    protected abstract String getIDFieldName();

    protected static class ScoredDocID {

        private final String id;
        private final float score;
        private final int rank;
        private final int indexID;

        ScoredDocID(int doc, String docId, int rank, float score) {
            indexID = doc;
            id = docId;
            this.rank = rank;
            this.score = score;
        }

        public String getId() {
            return id;
        }

        public float getScore() {
            return score;
        }

        public int getRank() {
            return rank;
        }

        public int getIndexID() {
            return indexID;
        }
    }
}
