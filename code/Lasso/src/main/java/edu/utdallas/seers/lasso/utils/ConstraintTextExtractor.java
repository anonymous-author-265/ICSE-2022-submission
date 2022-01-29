package edu.utdallas.seers.lasso.utils;

import edu.utdallas.seers.file.csv.CSVReader;
import edu.utdallas.seers.file.csv.CSVWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConstraintTextExtractor {

    private static final int CONTEXT_WINDOW = 200;

    private final Path spansFile;
    private final Path outputFile;
    private final Path dataDir;
    private final Pattern breakFinder = Pattern.compile("\\s*\\n+\\s*");

    public ConstraintTextExtractor(Path spansFile, Path dataDir, Path outputFile) {
        this.spansFile = spansFile;
        this.dataDir = dataDir;
        this.outputFile = outputFile;
    }

    public static void main(String[] args) {
        var spansFile = Paths.get(args[0]);
        var dataDir = Paths.get(args[1]);
        var outputFile = Paths.get(args[2]);

        new ConstraintTextExtractor(spansFile, dataDir, outputFile)
                .extract();
    }

    private void extract() {
        Map<String, List<ConstraintSpanFinder.ConstraintSpan>> constraints;
        try (var reader = CSVReader.create(spansFile, ConstraintSpanFinder.ConstraintSpan.class)) {
            constraints = reader.readAllRows()
                    .collect(Collectors.groupingBy(c -> c.system));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try (var writer = CSVWriter.<ConstraintSpanFinder.ConstraintSpan>create(outputFile)) {
            for (Map.Entry<String, List<ConstraintSpanFinder.ConstraintSpan>> entry : constraints.entrySet()) {
                var system = entry.getKey();
                var spans = entry.getValue();

                var docsDir = dataDir.resolve(system).resolve("docs-txt");

                writer.writeRows(spans.stream()
                        .peek(s -> setTexts(docsDir, s))
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setTexts(Path docsDir, ConstraintSpanFinder.ConstraintSpan span) {
        String fileContents;
        try {
            fileContents = Files.readString(docsDir.resolve(span.file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        span.text = breakFinder.matcher(fileContents.substring(span.start, span.end + 1))
                .replaceAll(" ");
        span.context = fileContents.substring(
                Math.max(0, span.start - CONTEXT_WINDOW),
                Math.min(fileContents.length(), span.end + CONTEXT_WINDOW)
        );
    }
}
