package edu.utdallas.seers.lasso.utils;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import edu.utdallas.seers.file.csv.CSVReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;

public class ConstraintSpanFinder {

    private final Path constraintsPath;
    private final Path textPath;
    private final Path outputPath;

    public ConstraintSpanFinder(Path constraintsPath, Path textPath, Path outputPath) {
        this.constraintsPath = constraintsPath;
        this.textPath = textPath;
        this.outputPath = outputPath;
    }

    public static void main(String[] args) {
        var constraintsPath = Paths.get(args[0]);
        var textPath = Paths.get(args[1]);
        var outputPath = Paths.get(args[2]);
        new ConstraintSpanFinder(constraintsPath, textPath, outputPath)
                .execute();
    }

    private Stream<ConstraintInput> loadTexts(Path csvFile) {
        return CSVReader.create(csvFile, ConstraintInput.class).readAllRows()
                .peek(c -> {
                    if (!c.constraintID.equals("PRE-RHI-1")) {
                        return;
                    }

                    var pattern = Pattern.compile("\\bu");

                    c.text = pattern.matcher(c.text)
                            .replaceAll("\\\\u");
                });
    }

    private void execute() {
        var constraints = loadTexts(constraintsPath)
                .collect(Collectors.toList());
        var spanMap = streamMap(constraints.stream().collect(Collectors.groupingBy(c -> c.system)))
                .flatMap(this::processSystem)
                .collect(Collectors.groupingBy(s -> s.constraintID));

        try (var writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (ConstraintInput constraint : constraints) {
                var id = constraint.constraintID;
                var spans = spanMap.getOrDefault(id, Collections.emptyList());
                System.out.println(id + "\t" + spans.size());
                if (spans.size() == 1) {
                    var span = spans.get(0);
                    writer.println(Stream.of(
                            constraint.constraintID,
                            span.file,
                            span.start,
                            span.end
                            )
                            .map(Objects::toString)
                            .collect(Collectors.joining("\t"))
                    );
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Stream<ConstraintSpan> processSystem(String project, List<ConstraintInput> constraints) {
        var systemPath = textPath.resolve(project).resolve("docs-txt");

        try {
            return Files.find(systemPath, Integer.MAX_VALUE, (p1, a) -> {
                if (!Files.isRegularFile(p1))
                    return false;

                var name = p1.getFileName().toString();
                return name.endsWith(".txt") || name.endsWith(".md");
            })
                    .flatMap(p -> searchInFile(systemPath, p, constraints));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private Stream<ConstraintSpan> searchInFile(Path systemPath, Path file, List<ConstraintInput> constraints) {
        var relativePath = systemPath.relativize(file);
        String fileContents;
        try {
            fileContents = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }

        return constraints.stream()
                .flatMap(c -> {
                    var matcher = Pattern.compile(c.text, Pattern.LITERAL).matcher(fileContents);
                    int start;
                    int end;
                    if (!c.context.isEmpty()) {
                        start = fileContents.indexOf(c.context);
                        if (start == -1) {
                            return Stream.empty();
                        }
                        end = start + c.context.length();
                    } else {
                        start = 0;
                        end = fileContents.length();
                    }
                    return matcher.results()
                            .filter(r -> r.start() >= start && r.start() <= end)
                            .map(r -> new ConstraintSpan(c.constraintID, relativePath, r.start(), r.end()));
                });
    }

    public static class ConstraintInput {
        @CsvBindByName(column = "System")
        String system;
        @CsvBindByName(column = "ID")
        String constraintID;
        @CsvBindByName(column = "Text")
        String text;
        /**
         * Larger window around text used to disambiguate some constraints.
         */
        @CsvBindByName(column = "Context")
        String context;
    }

    public static class ConstraintSpan {

        @CsvBindByName
        public String system;
        @CsvBindByName
        public String constraintID;
        @CsvCustomBindByName(converter = PathConverter.class)
        public Path file;
        @CsvBindByName
        public int start;
        @CsvBindByName
        public int end;
        @CsvBindByName
        public String text;
        @CsvBindByName
        public String context;

        @SuppressWarnings({"unused", "RedundantSuppression"})
        public ConstraintSpan() {
        }

        public ConstraintSpan(String id, Path file, int start, int end) {
            constraintID = id;
            this.file = file;
            this.start = start;
            this.end = end;
            system = "";
        }
    }

    public static class PathConverter extends AbstractBeanField<Path, Integer> {
        @Override
        protected Object convert(String value) {
            return Paths.get(value);
        }
    }
}
