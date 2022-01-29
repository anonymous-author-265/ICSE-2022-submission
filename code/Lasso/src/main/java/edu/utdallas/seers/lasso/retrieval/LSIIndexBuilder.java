package edu.utdallas.seers.lasso.retrieval;

import edu.utdallas.seers.parameter.Options;
import edu.utdallas.seers.retrieval.Index;
import edu.utdallas.seers.retrieval.SimpleRetrievalResult;
import edu.utdallas.seers.text.preprocessing.Preprocessing;
import org.apache.lucene.index.IndexableField;
import org.jooq.lambda.Unchecked;
import org.lucene_660_shaded.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.lucene_660_shaded.apache.lucene.document.Field;
import org.lucene_660_shaded.apache.lucene.document.StringField;
import org.lucene_660_shaded.apache.lucene.document.TextField;
import org.lucene_660_shaded.apache.lucene.index.DirectoryReader;
import org.lucene_660_shaded.apache.lucene.index.IndexWriter;
import org.lucene_660_shaded.apache.lucene.index.IndexWriterConfig;
import org.lucene_660_shaded.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LSA;
import pitt.search.semanticvectors.Search;
import pitt.search.semanticvectors.SearchResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.file.Files.createDirectories;
import static edu.utdallas.seers.file.Files.getTempFilePath;

public class LSIIndexBuilder extends BaselineIndexBuilder {

    private static final String TEXT_FIELD_NAME = "text";
    private static final String TERM_VECTORS_FILE_NAME = "term-vectors.bin";
    private static final String DOC_VECTORS_FILE_NAME = "doc-vectors.bin";

    private final Logger logger = LoggerFactory.getLogger(LSIIndexBuilder.class);

    public LSIIndexBuilder(Path sourcesPath, LassoScenarioID<BaselineConfig> key) {
        super(sourcesPath, key);
    }

    public static LSIIndex createIndex(Path path, LassoScenarioID<BaselineConfig> config) {
        return new LSIIndexBuilder(path, config).buildIndex();
    }

    private void setUpTempIndex(String indexName, Stream<? extends TextBlock> items, Path indexPath) {
        // Re-implementing this because we need to use the lucene that SV knows about, i.e. shaded
        logger.info("Setting up corpus for {}", indexName);
        try (var directory = FSDirectory.open(indexPath)) {
            if (DirectoryReader.indexExists(directory) &&
                    !Options.getInstance().isIgnoreCache()) {
                return;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var config = new IndexWriterConfig(new WhitespaceAnalyzer())
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (var writer = new IndexWriter(FSDirectory.open(indexPath), config)) {
            items.forEach(Unchecked.consumer(b -> writer.addDocument(Arrays.asList(
                    new StringField("id", b.getID(), Field.Store.YES),
                    new TextField(TEXT_FIELD_NAME, preprocessText(b.text), Field.Store.NO)
            ))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String createName(LassoScenarioID<BaselineConfig> key) {
        var config = key.getConfiguration();
        return String.format("%s_%s_%s_%d", config.type, key.project, config.output, config.dimension);
    }

    private void setUpSVIndex(Path indexPath, Path tempIndexPath) {
        logger.info("Setting up index at {}", indexPath);

        try {
            if (!Options.getInstance().isIgnoreCache() && Files.exists(indexPath) &&
                    Files.list(indexPath).count() != 0) {
                logger.info("Using existing index");
                return;
            }

            logger.info("Creating new index at {}", indexPath);

            var stopWordsFile = getTempFilePath("seers-LSI-stop-words.tmp");
            Files.write(stopWordsFile, Preprocessing.loadStandardStopWords());
            createDirectories(indexPath);
            LSA.main(new String[]{
                    "-luceneindexpath", tempIndexPath.toString(),
                    "-contentsfields", TEXT_FIELD_NAME,
                    "-docidfield", "id",
                    "-dimension", String.valueOf(key.getConfiguration().dimension),
                    "-stoplistfile", stopWordsFile.toString(),
                    "-termvectorsfile", indexPath.resolve(TERM_VECTORS_FILE_NAME).toString(),
                    "-docvectorsfile", indexPath.resolve(DOC_VECTORS_FILE_NAME).toString()
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Index<TextBlock> buildIndex(String indexName, Stream<? extends TextBlock> items) {
        var indexPath = resolveIndexPathForName(indexName);

        // SV takes an index as input
        var tempIndexPath = indexPath.getParent().resolve(indexPath.getFileName().toString() + "_temp");
        setUpTempIndex(indexName, items, tempIndexPath);
        setUpSVIndex(indexPath, tempIndexPath);

        return new LSIIndex(indexPath, key);
    }

    @Override
    public LSIIndex buildIndex() {
        return (LSIIndex) super.buildIndex();
    }

    @Override
    protected Index<TextBlock> createIndex(Path indexPath) {
        throw new IllegalStateException("Method not available");
    }

    @Override
    protected Optional<Iterable<IndexableField>> generateFields(TextBlock item, String indexName) {
        throw new IllegalStateException("Method not available");
    }

    public static class LSIIndex extends BaselineIndex {

        private final Path svIndexPath;

        public LSIIndex(Path path, LassoScenarioID<BaselineConfig> key) {
            super(key);
            svIndexPath = path;
        }

        @Override
        public List<SimpleRetrievalResult<TextBlock>> search(String text, String field) {
            var queryTerms = preprocessor.preprocess(text, true).collect(Collectors.toList());
            if (queryTerms.isEmpty()) {
                return Collections.emptyList();
            }

            var args = Stream.concat(
                    Stream.of("-queryvectorfile", svIndexPath.resolve(TERM_VECTORS_FILE_NAME).toString(),
                            "-searchvectorfile", svIndexPath.resolve(DOC_VECTORS_FILE_NAME).toString(),
                            "-numsearchresults", "100000"),
                    queryTerms.stream()
            )
                    .toArray(String[]::new);
            FlagConfig config = FlagConfig.getFlagConfig(args);

            List<SimpleRetrievalResult<TextBlock>> retList = new ArrayList<>();
            int rank = 1;
            for (SearchResult searchResult : Search.runSearch(config)) {
                String id = searchResult.getObjectVector().getObject().toString();

                TextBlock result = TextBlock.fromIDString(id);
                retList.add(new SimpleRetrievalResult<>(result, rank++, (float) searchResult.getScore()));
            }

            return retList;
        }
    }
}
