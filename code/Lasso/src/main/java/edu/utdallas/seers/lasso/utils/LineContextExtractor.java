package edu.utdallas.seers.lasso.utils;

import com.opencsv.CSVReaderHeaderAware;
import edu.utdallas.seers.file.LineReader;
import edu.utdallas.seers.lasso.data.entity.ASTPattern;
import org.jooq.lambda.Unchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;

/**
 * Takes line numbers from an input CSV and outputs a text file with the actual lines of code
 * plus some context, in the same order as the CSV.
 */
public class LineContextExtractor {
    // TODO: parameterize
    public static final List<String> LINES_COLUMNS = Collections.singletonList("Enforcing Statement");
    public static final String ID_COLUMN = "ID";
    public static final String SYSTEM_COLUMN = "Project";
    public static final int CONTEXT_SIZE = 5;

    private final Map<String, JavaFileNameMap> fileNameMaps = new ConcurrentHashMap<>();
    private final LineReader lineReader;
    private final Path dataDir;

    public LineContextExtractor(Path dataDir, int contextSize) {
        this.dataDir = dataDir;
        lineReader = new LineReader(contextSize);
    }

    public static void main(String[] args) throws IOException {
        var inputFile = Paths.get(args[0]);
        var dataDir = Paths.get(args[1]);
        var outputDir = Paths.get(args[2]);

        new LineContextExtractor(dataDir, CONTEXT_SIZE).extractLines(inputFile, outputDir);
    }

    private void extractLines(Path inputFile, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        try (PrintWriter linesWriter = new PrintWriter(Files.newBufferedWriter(outputDir.resolve("lines.txt")));
             PrintWriter idsWriter = new PrintWriter(Files.newBufferedWriter(outputDir.resolve("ids.txt")))) {
            var references = readReferences(inputFile);
            references.stream()
                    .flatMap(r -> lookUpLines(r, fileNameMaps.computeIfAbsent(r.system,
                            n -> JavaFileNameMap.buildFor(n, dataDir))))
                    .forEachOrdered(linesWriter::println);
            references.stream()
                    .map(r -> r.system + "\t" + r.id)
                    .forEachOrdered(idsWriter::println);
        }
    }

    private Stream<String> lookUpLines(ConstraintReference reference, JavaFileNameMap map) {
        var builder = Stream.<String>builder();

        builder.add("Constraint: " + reference.id);

        var lineGroups = reference.lineReferences.stream()
                .sorted(Comparator.comparing(r -> LINES_COLUMNS.indexOf(r.rangeName)))
                .collect(Collectors.toList());

        for (LineReference lineReference : lineGroups) {
            if (lineReference.file == null) {
                builder.add("--- ERROR ---");
                continue;
            }

            builder.add(String.format("COLUMN: %s", lineReference.rangeName));

            var filePath = map.lookUp(lineReference.file);
            if (filePath.isEmpty()) {
                builder.add(String.format("--- UNKNOWN FILE: %s ---", lineReference.file));
                continue;
            }

            builder.accept("File: " + lineReference.file);
            var previousLine = new AtomicInteger(-1);
            lineReader.readLines(filePath.get(), lineReference.lineNumbers)
                    .flatMap((i, s) -> {
                        String prefix;
                        if (lineReference.lineNumbers.contains(i)) {
                            prefix = " >";
                        } else {
                            prefix = "  ";
                        }
                        // FIXME extremely hacky! Instead partition stream into lists where each list is a contiguous range
                        var result = Stream.<String>builder();
                        if (previousLine.get() != -1 && previousLine.get() != i - 1) {
                            // Add a separator if we are jumping to a different range
                            result.accept("  --------");
                        }
                        previousLine.set(i);
                        return result.add(prefix + i + "\t" + s).build();
                    })
                    .forEachOrdered(builder);

            builder.accept("\n------------------\n");
        }

        builder.add("");

        return builder.build();
    }

    private List<ConstraintReference> readReferences(Path inputFile) {
        List<ConstraintReference> references;
        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {
            // Not using bean reader because we want to eventually parameterize the column
            var csvReader = new CSVReaderHeaderAware(reader);
            references = Stream.generate(Unchecked.supplier(csvReader::readMap))
                    .takeWhile(Objects::nonNull)
                    .map(m -> new ConstraintReference(
                                    m.get(SYSTEM_COLUMN),
                                    m.get(ID_COLUMN),
                                    streamMap(m)
                                            .filter((k, v) -> LINES_COLUMNS.contains(k))
                                            .toMap()
                            )
                    )
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return references;
    }

    public Stream<String> extractFor(String project, String constraintID, List<ASTPattern> patterns) {
        var pat1 = patterns.get(0);
        var fileName = pat1.getFileName();
        var patternLines = patterns.stream()
                .flatMap(p -> p.getLines().stream());
        // Add first line of method to show method name
        var methodHead = Stream.of(pat1.location.methodRange.begin.line);
        var lines = Stream.concat(methodHead, patternLines)
                .collect(Collectors.toSet());
        var ref = new ConstraintReference(project, constraintID, fileName, lines);

        return lookUpLines(ref, fileNameMaps.computeIfAbsent(project, n -> JavaFileNameMap.buildFor(n, dataDir)));
    }

    private static class ConstraintReference {
        private final String system;
        private final String id;
        private final List<LineReference> lineReferences;

        ConstraintReference(String system, String id, Map<String, String> rawLines) {
            this.system = system;
            this.id = id;
            lineReferences = streamMap(rawLines).flatMap((k, v) -> parseLines(k, v).stream()).toList();
        }

        public ConstraintReference(String project, String id, String fileName, Set<Integer> lines) {
            system = project;
            this.id = id;
            lineReferences = Collections.singletonList(new LineReference(fileName, lines));
        }

        private List<LineReference> parseLines(String rangeName, String rawLines) {
            // There can be multiple references in one cell
            var references = rawLines.split("\n");
            return Arrays.stream(references)
                    .map(rl -> new LineReference(rangeName, rl))
                    .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return "ConstraintReference{" +
                    "system='" + system + '\'' +
                    ", id='" + id + '\'' +
                    ", lineReferences=" + lineReferences +
                    '}';
        }
    }

    private static class LineReference {
        private final String rangeName;
        private final String file;
        private final Set<Integer> lineNumbers;

        LineReference(String rangeName, String rawLine) {
            this.rangeName = rangeName;
            var split = rawLine.split(":");
            if (split.length == 2) {
                file = split[0];
                lineNumbers = parseLineNumbers(split[1]);
            } else {
                file = null;
                lineNumbers = null;
            }
        }

        public LineReference(String fileName, Set<Integer> lines) {
            file = fileName;
            this.lineNumbers = lines;
            rangeName = "line";
        }

        private Set<Integer> parseLineNumbers(String string) {
            // Can have multiple ranges in a file separated by comma
            return Arrays.stream(string.split(","))
                    .flatMapToInt(s -> {
                        // Can either be a number or a range e.g. 2-5
                        var split = s.split("-");
                        assert split.length > 0 && split.length <= 2;

                        int first = Integer.parseInt(split[0]);
                        if (split.length == 2) {
                            // We have a range
                            return IntStream.rangeClosed(first, Integer.parseInt(split[1]));
                        } else {
                            // Just a value
                            return IntStream.of(first);
                        }
                    })
                    .distinct()
                    .boxed()
                    .collect(Collectors.toSet());
        }

        @Override
        public String toString() {
            return file + ":" +
                    lineNumbers.stream()
                            .map(String::valueOf).collect(Collectors.joining(","));
        }
    }

    private static class JavaFileNameMap {
        private final Logger logger = LoggerFactory.getLogger(JavaFileNameMap.class);

        private final Pattern fileNamePattern = Pattern.compile(".*/([^/]+)$");
        private final Map<String, List<Path>> map;

        public JavaFileNameMap(Map<String, List<Path>> map) {
            this.map = map;
        }

        public static JavaFileNameMap buildFor(String system, Path dataDir) {
            Stream<Path> walking;

            try {
                walking = Files.walk(dataDir.resolve(system + "/sources"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            var a = walking.filter(Files::isRegularFile)
                    .collect(Collectors.groupingBy(p -> p.getFileName().toString()));

            return new JavaFileNameMap(a);
        }

        public Optional<Path> lookUp(String filePath) {
            var matcher = fileNamePattern.matcher(filePath);
            if (!matcher.matches()) {
                logger.error("Malformed file: " + filePath);
                return Optional.empty();
            }
            var fileName = matcher.group(1);
            var candidates = map.get(fileName);

            if (candidates == null) return Optional.empty();

            Path result;

            if (candidates.size() > 1) {
                var filtered = candidates.stream()
                        .filter(c -> c.toString().endsWith(filePath))
                        .collect(Collectors.toList());
                assert filtered.size() > 0;

                if (filtered.size() > 1) {
                    logger.warn("Multiple candidates for {}: {}", filePath, filtered);
                }

                result = filtered.get(0);
            } else {
                result = candidates.get(0);
            }

            return Optional.of(result);
        }
    }
}
