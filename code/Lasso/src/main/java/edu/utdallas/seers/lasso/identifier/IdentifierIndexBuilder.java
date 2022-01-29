package edu.utdallas.seers.lasso.identifier;

import edu.utdallas.seers.json.AdapterSupplier;
import edu.utdallas.seers.json.JSON;
import edu.utdallas.seers.json.JSONSerializable;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;
import edu.utdallas.seers.parameter.Options;
import edu.utdallas.seers.retrieval.Index;
import edu.utdallas.seers.retrieval.IndexBuilder;
import edu.utdallas.seers.text.preprocessing.TextPreprocessor;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.similarities.Similarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IdentifierIndexBuilder extends IndexBuilder<Attribute> {

    /**
     * TODO this should be handled internally, other classes should not need to know this
     */
    public static final String TEXT_FIELD_NAME = "nameText";
    private static final String ID_FIELD_NAME = "id";

    private final Logger logger = LoggerFactory.getLogger(IdentifierIndexBuilder.class);

    private final IdentifierPreprocessor identifierPreprocessor = new IdentifierPreprocessor();
    private final String experimentName;
    private Map<Attribute, String> identifiers;
    private AtomicInteger idGenerator;
    private Map<String, List<String>> preprocessedTexts;

    public IdentifierIndexBuilder(String experimentName) {
        this.experimentName = experimentName;
    }

    @Override
    protected Index<Attribute> createIndex(Path indexPath) {
        Map<String, Attribute> inverted = identifiers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,
                        Map.Entry::getKey
                ));

        return new IdentifierIndex(indexPath, inverted, ID_FIELD_NAME, preprocessedTexts);
    }

    @Override
    protected Path resolveIndexPathForName(String indexName) {
        return Options.getInstance().getCachePath().resolve(indexName);
    }

    @Override
    public Index<Attribute> buildIndex(String indexName, Stream<? extends Attribute> items) {
        Path indexPath = resolveIndexPathForName(indexName);

        Path cachesPath = indexPath.resolve(experimentName + "-caches.json");

        if (!Options.getInstance().isIgnoreCache() && Files.exists(cachesPath)) {
            var maps = JSON.readJSON(cachesPath, Maps.class, Maps.getAdapterSupplier());
            this.identifiers = maps.identifiers.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getValue,
                            Map.Entry::getKey
                    ));

            this.preprocessedTexts = maps.preprocessedTexts;

            return createIndex(indexPath);
        }

        /* TODO this is a temporary solution, instead all items should be available to the
         *  index in something like a database where they can be read on demand, maybe adding a
         *  cache for performance. These 3 fields will be removed */
        identifiers = new HashMap<>();
        idGenerator = new AtomicInteger(1);
        preprocessedTexts = new HashMap<>();

        Index<Attribute> index = super.buildIndex(indexName, items);

        Map<String, Attribute> flip = identifiers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,
                        Map.Entry::getKey
                ));

        JSON.writeJSON(new Maps(flip, preprocessedTexts), cachesPath, false, Maps.getAdapterSupplier());

        return index;
    }

    /* TODO this subclass would not be needed by moving this method to the Variable class
     *   by creating a superclass of indexable entities with a method that returns the fields
     *   that need to be indexed using its own class e.g. Field */
    @Override
    protected Optional<Iterable<IndexableField>> generateFields(Attribute item, String indexName) {

        if (identifiers.containsKey(item)) {
            // FIXME: make all variables unique
            logger.error("Duplicated variable: {}", item);
            return Optional.empty();
        }

        String id = String.valueOf(idGenerator.getAndIncrement());
        identifiers.put(item, id);

        List<String> components = identifierPreprocessor
                .preprocessFullIdentifier(item.getIdentifier(), indexName)
                .collect(Collectors.toList());
        preprocessedTexts.put(item.getName(), components);

        String fieldText = String.join(" ", components);

        // Lucene analyzer must tokenize the string
        return Optional.of(
                Arrays.asList(
                        new StringField(ID_FIELD_NAME, id, Field.Store.YES),
                        new TextField(TEXT_FIELD_NAME, fieldText, Field.Store.YES)
                )
        );
    }

    @Override
    protected Optional<Similarity> getSimilarity() {
        return Optional.of(new PercentSimilarity());
    }

    public static class IdentifierPreprocessor {
        private static final Map<String, List<List<String>>> BASE_PACKAGES = new HashMap<>();

        static {
            addBasePackages("jedit-5.6pre0", Arrays.asList("org.jedit", "org.gjt.sp.jedit"));
            addBasePackages("apache-ant-1.10.6", Collections.singletonList("org.apache.tools.ant"));
            addBasePackages("joda_time-2.10.3", Collections.singletonList("org.joda.time"));
            addBasePackages("swarm-2.8.11", Arrays.asList(
                    "gov.usgs.volcanoes.winston",
                    "gov.usgs.volcanoes.core",
                    "gov.usgs.volcanoes.swarm"
            ));
            addBasePackages("apache-httpcomponents-4.5.9", Collections.singletonList("org.apache.http"));
            addBasePackages("guava-28.0", Collections.singletonList("com.google.common"));
            addBasePackages("argouml-0.35.4", Collections.singletonList("org.argouml"));
            addBasePackages("rhino-1.6R5", Collections.singletonList("org.mozilla.javascript"));
        }

        private final TextPreprocessor textPreprocessor = TextPreprocessor.withStandardStopWords();

        public IdentifierPreprocessor() {
        }

        private static void addBasePackages(String key, List<String> packages) {
            BASE_PACKAGES.put(
                    key,
                    packages.stream()
                            .map(s -> Arrays.asList(s.split("\\.")))
                            .collect(Collectors.toList()));
        }

        public Stream<String> preprocessFullIdentifier(Identifier item, String projectName) {
            Stream<String> tokens = removeBasePackage(item, projectName);

            return textPreprocessor.preprocess(tokens, true);
        }

        /**
         * If the identifier starts with one of the base packages of the system, trim that.
         * Otherwise trim all but the last package component.
         *
         * @param fullIdentifier Identifier.
         * @param projectName    Name of project.
         * @return Trimmed identifier components.
         */
        private Stream<String> removeBasePackage(Identifier fullIdentifier, String projectName) {
            var basePackages = BASE_PACKAGES.get(projectName);

            List<String> packageComponents = fullIdentifier.getPackageComponents();

            int trimAmount = basePackages.stream()
                    .filter(fullIdentifier::packageStartsWith)
                    .map(List::size)
                    .findAny()
                    .orElseGet(() -> packageComponents.size() > 0 ? packageComponents.size() - 1 : 0);

            return fullIdentifier.stream()
                    .skip(trimAmount);
        }
    }

    public static class Maps implements JSONSerializable<AdapterSupplier> {
        public Map<String, Attribute> identifiers;
        public Map<String, List<String>> preprocessedTexts;

        public Maps(Map<String, Attribute> identifiers, Map<String, List<String>> preprocessedTexts) {
            this.identifiers = identifiers;
            this.preprocessedTexts = preprocessedTexts;
        }

        private static AdapterSupplier getAdapterSupplier() {
            return new AdapterSupplier() {
                @Override
                protected Builder build(Builder builder) {
                    return builder.addHierarchyAdapter(Attribute.class, Attribute::fromString);
                }
            };
        }
    }

}
