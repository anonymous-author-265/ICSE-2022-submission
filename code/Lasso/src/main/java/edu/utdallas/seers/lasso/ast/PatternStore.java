package edu.utdallas.seers.lasso.ast;

import edu.utdallas.seers.json.AdapterSupplier;
import edu.utdallas.seers.json.JSON;
import edu.utdallas.seers.json.JSONSerializable;
import edu.utdallas.seers.lasso.data.entity.*;
import edu.utdallas.seers.lasso.data.entity.constants.Constant;
import edu.utdallas.seers.lasso.data.entity.variables.Attribute;
import edu.utdallas.seers.parameter.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PatternStore implements JSONSerializable<PatternStore.Supplier> {

    private static final Logger logger = LoggerFactory.getLogger(PatternStore.class);
    /**
     * Used for reference in the JSON file.
     */
    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private final String projectName;

    private final Map<BasicPatternRecord, ASTPattern> records = new HashMap<>();
    private final Map<ValuePatternRecord, List<ValueASTPattern>> valueASTPatterns = new HashMap<>();
    private final Map<NameValuePatternRecord, List<NameValueASTPattern>> nameValuePatterns = new HashMap<>();

    public PatternStore(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Creates a new store by parsing the system source or by loading a previously cached instance.
     *
     * @param sourcesDir  Path with sources for all systems.
     * @param projectName Name of the system.
     * @param cachePath   Path where stores will be cached after being constructed.
     * @return A pattern store for the system.
     */
    public static PatternStore create(Path sourcesDir, String projectName, Path cachePath) {
        Path storePath = cachePath.resolve(projectName + ".json");
        var ignoreCache = Options.getInstance().isIgnoreCache();

        // Return cached store if it exists
        if (Files.exists(storePath) && !ignoreCache) {
            logger.info("Loading cached store from " + storePath);
            return JSON.readJSON(storePath, PatternStore.class, new Supplier());
        }

        logger.info("Running AST detector");

        var patterns = ASTPatternDetector.detect(sourcesDir, projectName, p -> true)
                .flatMap(t -> t.v2)
                .map(p -> p.match)
                .collect(Collectors.toList());

        PatternStore store = new PatternStore(projectName);

        // TODO can take a stream of patterns instead
        store.addPatterns(patterns);

        // Cache the store
        logger.info("Caching pattern store at " + storePath);
        JSON.writeJSON(store, storePath, false, new Supplier());

        return store;
    }

    private void addPatterns(Collection<? extends ASTPattern> patterns) {
        for (ASTPattern pattern : patterns) {
            pattern.accept(this);
        }
    }

    public void addPattern(ASTPattern pattern) {
        records.putAll(pattern.getLines().stream()
                .map(l -> new BasicPatternRecord(pattern.getPatternType(), pattern.getFileName(), l))
                .collect(Collectors.toMap(
                        k -> k,
                        k -> pattern
                )));
    }

    public void addPattern(ValueASTPattern pattern) {
        // TODO we wouldn't need these here if the detectors were of 3 kinds: IR + check, slice + AST, IR + AST
        //  We're only doing this because we are checking ALL pattern types against records.
        //  Slice + AST and IR + AST patterns don't need to check records
        addPattern(pattern);

        List<ValueASTPattern> list = valueASTPatterns.computeIfAbsent(
                new ValuePatternRecord(pattern),
                v -> new ArrayList<>()
        );

        list.add(pattern);
    }

    public void addPattern(NameValueASTPattern pattern) {
        addPattern(pattern);

        List<NameValueASTPattern> list = nameValuePatterns.computeIfAbsent(
                new NameValuePatternRecord(pattern),
                v -> new ArrayList<>()
        );

        list.add(pattern);
    }

//    public boolean contains(Pattern pattern) {
//        Optional<PatternSingleLineFormat> patternLine = pattern.toSingleLineFormat();
//
//        return patternLine.map(this::contains)
//                .orElse(false);
//    }

    public boolean contains(PatternSingleLineFormat pattern) {
        return records.containsKey(new BasicPatternRecord(
                pattern.getpType(),
                pattern.getFile(),
                pattern.getLineNum()
        ));
    }

//    public Optional<ASTPattern> lookUpInstance(Pattern pattern) {
//        Optional<PatternSingleLineFormat> lineFormat = pattern.toSingleLineFormat();
//        return lineFormat.map(l -> records.get(new BasicPatternRecord(
//                l.getpType(),
//                l.getFile(),
//                l.getLineNum()
//        )));
//    }

    public List<ValueASTPattern> lookUpInstances(PatternType patternType, Constant<?> value) {
        return valueASTPatterns
                .getOrDefault(new ValuePatternRecord(patternType, value), Collections.emptyList());
    }

    public List<NameValueASTPattern> lookUpInstances(PatternType patternType, Constant<?> value, Attribute name) {
        return nameValuePatterns
                .getOrDefault(new NameValuePatternRecord(patternType, value, name), Collections.emptyList());
    }

    private static class ValuePatternRecord {
        protected static final String SEPARATOR = ";;";

        private final PatternType patternType;
        private final Constant<?> constant;

        public ValuePatternRecord(ValueASTPattern pattern) {
            patternType = pattern.getPatternType();
            constant = pattern.getConstant();
        }

        public ValuePatternRecord(String string) {
            String[] split = string.split(SEPARATOR, 2);
            patternType = PatternType.valueOf(split[0]);
            constant = Constant.fromString(split[1]);
        }

        public ValuePatternRecord(PatternType patternType, Constant<?> constant) {
            this.patternType = patternType;
            this.constant = constant;
        }

        @Override
        public String toString() {
            return patternType + SEPARATOR + constant;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValuePatternRecord that = (ValuePatternRecord) o;
            return patternType == that.patternType &&
                    constant.equals(that.constant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patternType, constant);
        }
    }

    private static class NameValuePatternRecord {

        protected static final String SEPARATOR = ";;";

        private final PatternType patternType;
        private final Constant<?> constant;
        private final Attribute attribute;

        public NameValuePatternRecord(NameValueASTPattern pattern) {
            patternType = pattern.getPatternType();
            constant = pattern.getConstant();
            attribute = pattern.getVariable();
        }

        public NameValuePatternRecord(String string) {
            String[] split = string.split(SEPARATOR, 3);
            patternType = PatternType.valueOf(split[0]);
            attribute = Attribute.fromString(split[1]);
            constant = Constant.fromString(split[2]);
        }

        public NameValuePatternRecord(PatternType patternType, Constant<?> constant, Attribute attribute) {
            this.patternType = patternType;
            this.constant = constant;
            this.attribute = attribute;
        }

        @Override
        public String toString() {
            // Constant has to go at the end because empty string constant causes trouble
            return String.join(SEPARATOR,
                    patternType.toString(), attribute.toString(), constant.toString());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NameValuePatternRecord that = (NameValuePatternRecord) o;
            return patternType == that.patternType &&
                    constant.equals(that.constant) &&
                    attribute.equals(that.attribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patternType, constant, attribute);
        }
    }

    // TODO merge this class with PatternSingleLineFormat
    private static class BasicPatternRecord {

        private static final String SEPARATOR = ";";
        private final PatternType patternType;
        private final String file;
        private final int line;

        public BasicPatternRecord(PatternType patternType, String file, int line) {
            this.patternType = patternType;
            this.file = file;
            this.line = line;
        }

        public BasicPatternRecord(String string) {
            String[] split = string.split(SEPARATOR);
            patternType = PatternType.valueOf(split[0]);
            file = split[1];
            line = Integer.parseInt(split[2]);
        }

        @Override
        public String toString() {
            return String.join(SEPARATOR, patternType.toString(), file, String.valueOf(line));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BasicPatternRecord that = (BasicPatternRecord) o;
            return line == that.line &&
                    patternType == that.patternType &&
                    file.equals(that.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patternType, file, line);
        }
    }

    public static class Supplier extends AdapterSupplier {
        @Override
        protected Builder build(Builder builder) {
            return builder
                    .addTypeAdapter(ValuePatternRecord.class, ValuePatternRecord::new)
                    .addTypeAdapter(NameValuePatternRecord.class, NameValuePatternRecord::new)
                    .addTypeAdapter(BasicPatternRecord.class, BasicPatternRecord::new)
                    .addPathAdapter()
                    .addHierarchyAdapter(Constant.class, Constant::fromString)
                    .addHierarchyAdapter(Attribute.class, Attribute::fromString);
        }
    }
}
