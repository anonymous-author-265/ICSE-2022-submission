package edu.utdallas.seers.lasso.data;

import com.opencsv.bean.AbstractCsvConverter;
import com.opencsv.bean.CsvBindAndSplitByName;
import com.opencsv.bean.CsvBindByName;
import edu.utdallas.seers.file.csv.CSVReader;
import edu.utdallas.seers.lasso.data.entity.*;
import edu.utdallas.seers.stream.PairSeq;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.collection.Collections.streamMap;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class ConstraintLoader {
    private final Logger logger = LoggerFactory.getLogger(ConstraintLoader.class);

    private final Pattern scrDirPattern = Pattern.compile("^.*src/(main/)*(java/)*");
    private final Pattern methodParamsPattern = Pattern.compile("\\(.*\\)");

    /**
     * Assumes that a package name will never contain caps.
     */
    private final Pattern nestedClassPattern = Pattern.compile("^[a-z_.]+\\.[A-Z][A-Za-z_]*\\.([A-Z].*)");

    /**
     * Directly modify an input.
     */
    private final Map<Tuple2<String, String>, String> inputMods = new HashMap<>();

    private final Set<String> implementedDetectors = Arrays.stream(PatternType.values())
            .map(PatternType::toInputString)
            .collect(Collectors.toSet());

    {
        // fixme opencsv is detecting \n as an escaped n if the field is not quoted, which is the way google exports it
        inputMods.put(tuple("Rhino-3", "n"), "\n");
        inputMods.put(tuple("SWARM-8", "javax.swing.filechooser.FileNameExtensionFilter"), "javax.swing.filechooser.FileNameExtensionFilter#<init>");
        inputMods.put(tuple("argouml-17", "javax.swing#JCheckBox"), "javax.swing.JCheckBox#<init>");
        inputMods.put(tuple("argouml-20", "javax.swing#JCheckBox"), "javax.swing.JCheckBox#<init>");
        inputMods.put(tuple("argouml-53", "!org.argouml.core.propertypanels.ui.RowSelector#getModel#getSize -  1"),
                "!org.argouml.core.propertypanels.ui.RowSelector#access$300@getSize -  1");
        inputMods.put(tuple("SWARM-54", "gov.usgs.volcanoes.swarm.event.PickMenu#createPickMenu#weight"),
                "gov.usgs.volcanoes.swarm.event.PickMenu$2#val$weight");
        inputMods.put(tuple("joda-time-9", "org.joda.time.DateTime"), "org.joda.time.DateTime#<init>");
        inputMods.put(tuple("joda-time-10", "org.joda.time.DateTime"), "org.joda.time.DateTime#<init>");
        inputMods.put(tuple("joda-time-18", "org.joda.time.DateTime"), "org.joda.time.DateTime#<init>");
        inputMods.put(tuple("joda-time-12", "org.joda.time.Instant#Instant(long)"), "org.joda.time.Instant#<init>");
        inputMods.put(tuple("HTTPCore-26", "org.apache.http.pool.AbstractConnPool#AbstractConnPool"), "org.apache.http.pool.AbstractConnPool#<init>");
    }

    public static Set<Path> loadExclusions(Path projectPath) {
        Set<Path> excludedPaths;
        try (Stream<String> lines = Files.lines(projectPath.getParent().resolve("exclude.txt"))) {
            excludedPaths = lines
                    .map(Paths::get)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return excludedPaths;
    }

    public Stream<PatternEntry> loadConstraints(Path csvFile) {
        return CSVReader.create(csvFile, ConstraintRow.class)
                .readAllRows()
                // We are excluding some of the constraints because they can't be found in Java code or are not constraints
                // Only allowed ones with implemented detectors
                .filter(r -> !"not-app-code".equals(r.groundTruthType) &&
                        (r.pattern == null ||
                                implementedDetectors.contains(r.pattern)))
                .map(this::convertRow);
    }

    public PairSeq<String, List<PatternEntry>> loadGrouped(Path csvFile) {
        var map = loadConstraints(csvFile)
                .collect(Collectors.groupingBy(PatternEntry::getSystem));

        logger.info("Loaded {} constraints from {} systems",
                map.values().stream().mapToInt(List::size).sum(),
                map.size()
        );

        return streamMap(map);
    }

    private PatternEntry convertRow(ConstraintRow row) {
        List<DetectorInput> inputs = Stream.of(row.input1, row.input2, row.input3)
                .filter(s -> s != null && !s.isEmpty())
                .map(input -> DetectorInput.parseInput(convertInput(input, row.id)))
                .collect(Collectors.toList());

        return new PatternEntry(
                row.id,
                convertGroundTruths(row.truths),
                inputs,
                row.system,
                ConstraintType.fromString(row.constraintType),
                Optional.ofNullable(row.pattern).map(PatternType::fromString).orElse(null),
                row.context,
                row.text,
                row.operands,
                Optional.ofNullable(row.consequence).orElse(""),
                row.extra
        );
    }

    /**
     * Converts each of the inputs for the detector.
     *
     * @param input        Input string.
     * @param constraintID ID of the constraint.
     * @return Converted string.
     */
    private String convertInput(String input, String constraintID) {
        // Convert "" to actual empty string
        if (input.equals("\"\"")) {
            return "";
        }

        Tuple2<String, String> key = tuple(constraintID, input);
        if (inputMods.containsKey(key)) {
            return inputMods.get(key);
        }

        // Method parameters to disambiguate methods are not being used
        Matcher matcher = methodParamsPattern.matcher(input);

        String replaced = input;

        if (matcher.find()) {
            matcher.reset();
            replaced = matcher.replaceFirst("");
        }

        // Can combine method parameter removal + @
        if (replaced.startsWith("!")) {
            int index = replaced.lastIndexOf("#");

            return replaced.substring(0, index) + "@" + replaced.substring(index + 1);
        }

        matcher = nestedClassPattern.matcher(replaced);

        if (matcher.find()) {
            String suffix = matcher.group(1);

            replaced = replaced.replace("." + suffix, "$" + suffix);
        }

        return replaced;
    }

    /**
     * Trims paths from 'src/' or similar, because the compiled classes don't have the full path.
     * TODO: check that these still refer to the same class, since sometimes there are duplicates in project
     *
     * @param truths Ground truths from spreadsheet
     * @return Converted ground truths.
     */
    private PatternTruth[] convertGroundTruths(List<PatternTruth> truths) {
        return truths.stream()
                .map(pt -> {
                    String file = pt.getFile();

                    Matcher matcher = scrDirPattern.matcher(file);
                    String processedFile = matcher.replaceFirst("");

                    return new PatternTruth(processedFile, pt.getLines());
                })
                .toArray(PatternTruth[]::new);
    }

    /**
     * Exists to easily load rows from CSV. Must be public so that opencsv can access it.
     */
    public static class ConstraintRow {
        @CsvBindByName(column = "Project")
        String system;

        @CsvBindByName(column = "ID")
        String id;
        @CsvBindAndSplitByName(column = "Enforcing Statement", splitOn = "\n",
                elementType = PatternTruth.class, converter = CsvConverter.class)
        List<PatternTruth> truths;
        @CsvBindByName(column = "Part 1")
        String input1;
        @CsvBindByName(column = "Part 2")
        String input2;
        @CsvBindByName(column = "Part 3")
        String input3;
        @CsvBindByName(column = "Ground Truth Type")
        String groundTruthType;

        @CsvBindByName(column = "Enforcing Statement CIP")
        String pattern;

        @CsvBindByName(column = "Constraint Type")
        String constraintType;

        @CsvBindByName(column = "Text")
        String text;

        @CsvBindByName(column = "Context")
        String context;

        @CsvBindAndSplitByName(column = "Operands", splitOn = "\\s*,\\s*", elementType = String.class)
        List<String> operands;

        @CsvBindByName(column = "Consequence")
        String consequence;

        @CsvBindByName(column = "Data Set")
        String extra;
    }

    public static class CsvConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value) {
            return PatternTruth.fromString(value);
        }
    }
}