package edu.utdallas.seers.retrieval;

import edu.utdallas.seers.file.Files;
import edu.utdallas.seers.parameter.Options;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.jooq.lambda.Unchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class IndexBuilder<T extends Retrievable> {
    final Logger logger = LoggerFactory.getLogger(IndexBuilder.class);

    // TODO do not build index if existing
    public Index<T> buildIndex(String indexName, Stream<? extends T> items) {
        logger.info("Setting up index for {}", indexName);
        var indexPath = resolveIndexPathForName(indexName);
        try {
            if (!Options.getInstance().isIgnoreCache() &&
                    DirectoryReader.indexExists(FSDirectory.open(indexPath))) {
                logger.info("Using existing index at: {}", indexPath);
                return createIndex(indexPath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        logger.info("Creating new index at: {}", indexPath);

        IndexWriterConfig writerConfig = new IndexWriterConfig()
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        getSimilarity().ifPresent(writerConfig::setSimilarity);

        try (IndexWriter indexWriter = new IndexWriter(FSDirectory.open(indexPath), writerConfig)) {
            items.forEach(t -> {
                Optional<Iterable<IndexableField>> fields = generateFields(t, indexName);

                fields.ifPresent(Unchecked.consumer(indexWriter::addDocument));

                if (fields.isEmpty()) {
                    logger.warn("Item not indexed: {}", t);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return createIndex(indexPath);
    }

    protected Path resolveIndexPathForName(String indexName) {
        return Files.getTempFilePath("lucene-indexes", indexName);
    }

    protected abstract Index<T> createIndex(Path indexPath);

    /**
     * Generate the fields for indexing an entity, or an empty optional if the entity should not be
     * indexed.
     *
     * @param item      The entity.
     * @param indexName Name of the index.
     * @return Optional of fields or empty.
     */
    protected abstract Optional<Iterable<IndexableField>> generateFields(T item, String indexName);

    /**
     * Similarity to use for Lucene's IndexBuilder. If an empty optional is returned, the default
     * is used.
     *
     * @return Similarity.
     */
    protected Optional<Similarity> getSimilarity() {
        return Optional.empty();
    }
}
