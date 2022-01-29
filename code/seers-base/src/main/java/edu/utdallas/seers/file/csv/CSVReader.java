package edu.utdallas.seers.file.csv;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Reader that encapsulates underlying file system reader management to unburden clients.
 *
 * @param <T> Class should be marked with opencsv's annotations, e.g.
 *            {@link com.opencsv.bean.CsvBindByName}
 */
public class CSVReader<T> implements Closeable {

    private final CsvToBean<T> parser;
    private final Reader reader;

    private CSVReader(Reader reader, Class<T> type) {
        this.reader = reader;
        parser = new CsvToBeanBuilder<T>(reader)
                .withType(type)
                .build();
    }

    public static <T> CSVReader<T> tryCreate(Path source, Class<T> rowType) throws IOException {
        BufferedReader reader = Files.newBufferedReader(source);

        return new CSVReader<>(reader, rowType);
    }

    public static <T> CSVReader<T> create(Path source, Class<T> rowType) {
        try {
            return tryCreate(source, rowType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Stream<T> readAllRows() {
        return parser.stream();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
