package edu.utdallas.seers.lasso.stats;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seers.textanalyzer.TextProcessor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SentenceStatsExtractor {

    private final Logger logger = LoggerFactory.getLogger(SentenceStatsExtractor.class);

    private final Path dataDir;
    private final Path outputFile;
    private final Path constraintsPath;
    private final Path codedDocsPath;

    public SentenceStatsExtractor(Path constraintsPath, Path codedDocsPath, Path dataDir, Path outputFile) {
        this.constraintsPath = constraintsPath;
        this.codedDocsPath = codedDocsPath;
        this.dataDir = dataDir;
        this.outputFile = outputFile;
    }

    public static void main(String[] args) throws IOException {
        var constraintsPath = Paths.get(args[0]);
        var codedDocsPath = Paths.get(args[1]);
        var dataDir = Paths.get(args[2]);
        var outputFile = Paths.get(args[3]);
        new SentenceStatsExtractor(constraintsPath, codedDocsPath, dataDir, outputFile)
                .extractStats();
    }

    private void extractStats() throws IOException {
        var constraints = readConstraints();
        var codedDocs = readCodedDocs();
        var results = Files.find(
                dataDir, 2,
                (p, a) -> Files.isDirectory(p) && p.getFileName().toString().equals("docs-txt")
        )
                .flatMap(p -> {
                    var project = p.getParent().getFileName().toString();
                    return processProject(
                            p,
                            constraints.get(project),
                            codedDocs.getOrDefault(project, Collections.emptyList())
                    );
                });

        logger.info("Calculating stats");

        try (var writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            writer.println(String.join("\t",
                    Arrays.asList("Project", "File", "Line Range (zero-based)", "Constraints",
                            "Sentences", "Constraints per Sentence")));
            results.forEachOrdered(r -> writer.println(String.join("\t",
                    Arrays.asList(
                            r.project,
                            r.file.toString(),
                            r.range,
                            String.valueOf(r.constraints),
                            String.valueOf(r.sentences),
                            String.valueOf((float) r.constraints / r.sentences)
                    )
            )));
        }
    }

    private Stream<Result> processProject(Path path, List<Input> constraints, List<CodedDoc> docList) {
        var project = path.getParent().getFileName().toString();
        var codedDocs = docList.stream()
                .collect(Collectors.toMap(d -> d.file, d -> d));

        logger.info("Project: {}", project);

        Stream<Path> files;
        try {
            files = Files.find(path, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var byFile = constraints.stream()
                .collect(Collectors.groupingBy(i -> i.file));

        return files.filter(p -> codedDocs.isEmpty() || codedDocs.containsKey(path.relativize(p).toString()))
                .map(p -> {
                    logger.info("Processing " + p);
                    var codedDoc = codedDocs.get(path.relativize(p).toString());
                    String fileText = readFile(p, codedDoc);
                    var sentences = TextProcessor.processTextFullPipeline(fileText, false)
                            .size();

                    var relativePath = path.relativize(p);
                    var range = Optional.ofNullable(codedDoc)
                            .map(d -> d.lineRange)
                            .map(r -> Arrays.stream(r)
                                    .mapToObj(String::valueOf)
                                    .collect(Collectors.joining("-")))
                            .orElse("FULL");
                    return new Result(project, relativePath, range, sentences,
                            byFile.getOrDefault(relativePath, Collections.emptyList()).size());
                })
                // These files are too short to matter
                .filter(r -> r.sentences >= 5);
    }

    private String readFile(Path file, CodedDoc codedDoc) {
        String fileText;
        try {
            if (codedDoc == null || codedDoc.lineRange == null) {
                fileText = Files.readString(file);
            } else {
                var range = codedDoc.lineRange;
                fileText = Files.lines(file)
                        .limit(range[1] + 1)
                        .skip(range[0])
                        .collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return fileText;
    }

    private Map<String, List<CodedDoc>> readCodedDocs() throws IOException {
        try (var reader = new CSVReaderHeaderAware(Files.newBufferedReader(codedDocsPath))) {
            return readCSV(reader)
                    .map(CodedDoc::new)
                    .filter(d -> !d.file.equals("ALL"))
                    .collect(Collectors.groupingBy(d -> d.project));
        }
    }

    private Map<String, List<Input>> readConstraints() throws IOException {
        try (var reader = new CSVReaderHeaderAware(Files.newBufferedReader(constraintsPath))) {
            return readCSV(reader)
                    .map(Input::new)
                    .collect(Collectors.groupingBy(i -> i.project));
        }
    }

    private Stream<Map<String, String>> readCSV(CSVReaderHeaderAware reader) throws IOException {
        return Stream.generate(() -> {
            try {
                return reader.readMap();
            } catch (IOException | CsvValidationException e) {
                throw new RuntimeException("a");
            }
        })
                .takeWhile(Objects::nonNull);
    }

    private static class Result {

        private final Path file;
        private final int sentences;
        private final String project;
        private final int constraints;
        private final String range;

        public Result(String project, Path file, String range, int sentences, int constraints) {
            this.project = project;
            this.file = file;
            this.range = range;
            this.sentences = sentences;
            this.constraints = constraints;
        }
    }

    private static class Input {

        private final String project;
        private final Path file;

        public Input(Map<String, String> map) {
            project = map.get("System");
            file = Paths.get(map.get("File"));
        }
    }

    private static class CodedDoc {

        private final String project;
        private final String file;
        /**
         * Zero-based.
         */
        private final int[] lineRange;

        public CodedDoc(Map<String, String> map) {
            project = map.get("Project");
            file = map.get("File");
            lineRange = Optional.of(map.get("Line Range"))
                    .filter(s -> !s.isEmpty())
                    // Input indexes are 1-based
                    .map(s -> Arrays.stream(s.split("-"))
                            .mapToInt(s2 -> Integer.parseInt(s2) - 1)
                            .toArray())
                    .orElse(null);
        }
    }
}
