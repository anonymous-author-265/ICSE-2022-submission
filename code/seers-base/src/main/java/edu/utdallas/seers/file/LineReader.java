package edu.utdallas.seers.file;

import edu.utdallas.seers.stream.PairSeq;
import org.jooq.lambda.Seq;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LineReader {
    private final int contextSize;

    public LineReader(int contextSize) {
        this.contextSize = contextSize;
    }

    /**
     * Extracts lines from a file. Each line is extracted with the amount of lines around it
     * specified as {@link LineReader#contextSize}. Context lines that would overlap are not
     * repeated in the output.
     *
     * @param filePath    The file.
     * @param lineNumbers 1-based line numbers.
     * @return Enumerated lines in order.
     */
    public PairSeq<Integer, String> readLines(Path filePath, Set<Integer> lineNumbers) {
        List<String> fileLines;
        try {
            fileLines = Files.lines(filePath).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return readLines(fileLines, lineNumbers);
    }

    /**
     * @see LineReader#readLines(Path, Set)
     */
    public PairSeq<Integer, String> readLines(List<String> fileLines, Set<Integer> lineNumbers) {
        var totalLines = fileLines.size();

        var finalLineNumbers = Seq.seq(lineNumbers)
                .flatMap(i -> Seq.rangeClosed(
                        Math.max(1, i - contextSize),
                        Math.min(totalLines, i + contextSize))
                )
                .sorted()
                .distinct();

        // i is 1-based, must convert to 0 based for list
        return PairSeq.seq(finalLineNumbers, i -> i, i -> fileLines.get(i - 1));
    }
}