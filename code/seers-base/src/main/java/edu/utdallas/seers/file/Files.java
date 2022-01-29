package edu.utdallas.seers.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class Files {
    private Files() {
    }

    /**
     * Returns the path of a file in the default temp directory of the system.
     *
     * @param first first component of the file path.
     * @param more  optional additional components of the file path.
     * @return a new Path.
     */
    public static Path getTempFilePath(String first, String... more) {
        return Paths.get(System.getProperty("java.io.tmpdir"))
                .resolve(Paths.get(first, more));
    }

    public static Path createDirectories(Path path) {
        try {
            return java.nio.file.Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void deleteRecursively(Path path) {
        if (!java.nio.file.Files.exists(path)) {
            return;
        }

        var visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                java.nio.file.Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                java.nio.file.Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        };

        try {
            java.nio.file.Files.walkFileTree(path, visitor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> loadResourceLines(String resourceName) {
        var stream = Objects.requireNonNull(
                Files.class.getClassLoader()
                        .getResourceAsStream(resourceName)
        );

        try (var reader = new BufferedReader(new InputStreamReader(stream))) {
            return reader.lines().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
