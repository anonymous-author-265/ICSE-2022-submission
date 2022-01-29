package edu.utdallas.seers.lasso.experiment;

import edu.utdallas.seers.file.csv.CSVWriter;
import edu.utdallas.seers.lasso.ast.PatternStore;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.PatternOutputFormat;
import edu.utdallas.seers.lasso.data.entity.PatternSingleLineFormat;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static edu.utdallas.seers.file.Files.getTempFilePath;

public class PatternDetectionRecallEvaluator {
    private final Path sourcesPath;
    private final Path cacheDir;

    public PatternDetectionRecallEvaluator(Path sourcesPath, Path cacheDir) {
        this.sourcesPath = sourcesPath;
        this.cacheDir = cacheDir;
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("PatternDetectionRecallEvaluator").build().defaultHelp(true);

        parser.addArgument("constraintsPath")
                .help("csv of constraints with ground truth.");

        parser.addArgument("sourcesPath")
                .help("Path of the target systems' source code. e.g. data directory");

        parser.addArgument("destPath")
                .help("Directory where evaluation will be written");

        parser.addArgument("-c", "--cache-dir")
                .help("Cache files/debug directory")
                .setDefault(getTempFilePath("detector-cache").toString());

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return;
        }

        var cacheDir = Paths.get(namespace.getString("cache_dir"));
        var sourcesPath = Paths.get(namespace.getString("sourcesPath"));
        new PatternDetectionRecallEvaluator(sourcesPath, cacheDir)
                .runExperiment(
                        Paths.get(namespace.getString("constraintsPath")),
                        Paths.get(namespace.getString("destPath"))
                );
    }

    private void runExperiment(Path constraintsPath, Path destPath) {
        Path byConstraintPath = destPath.resolve("Evaluation-by-constraint.csv");

        try (Stream<PatternOutputFormat> results = runDetectors(constraintsPath);

             CSVWriter<PatternOutputFormat.ResultEvaluation> evaluationWriter =
                     CSVWriter.tryCreate(byConstraintPath)
        ) {
            results.forEach(r -> evaluationWriter.writeRows(
                    Stream.of(r.toResultEvaluation())
            ));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private Stream<PatternOutputFormat> runDetectors(Path constraintsPath) throws IOException {
        return new ConstraintLoader()
                .loadGrouped(constraintsPath)
                .flatMap(p -> {
                    var projectName = p.v1;
                    var constraints = p.v2;
                    if (constraints.isEmpty()) {
                        return Stream.empty();
                    }

                    PatternStore patternStore = PatternStore.create(
                            this.sourcesPath,
                            projectName,
                            cacheDir.resolve("pattern-cache")
                    );

                    List<PatternSingleLineFormat> empty = Collections.emptyList();

                    return constraints.stream()
                            .map(c -> {
                                        List<PatternSingleLineFormat> patternSingleLineFormats = c.getgrdTruthinSingleLineFormat();
                                        Optional<PatternSingleLineFormat> found = patternSingleLineFormats.stream()
                                                .filter(patternStore::contains)
                                                .findFirst();
                                        return found.map(psl -> new PatternOutputFormat(c, Collections.singletonList(psl), empty, empty))
                                                .orElseGet(() -> new PatternOutputFormat(c, empty, empty, Collections.singletonList(patternSingleLineFormats.get(0))));
                                    }
                            );
                });
    }
}
