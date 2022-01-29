package edu.utdallas.seers.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom implementation of {@link Files#walk(Path, FileVisitOption...)} because it doesn't allow
 * skipping subtrees.
 */
public class JavaFileWalker {

    final Logger logger = LoggerFactory.getLogger(JavaFileWalker.class);

    private final Set<Path> excludedPaths;
    private final Path projectPath;

    public JavaFileWalker(Path projectPath, Set<Path> excludedPaths) {
        this.projectPath = projectPath;
        this.excludedPaths = excludedPaths;
    }

    public static Stream<Path> walk(Path projectPath, Set<Path> excludedPaths) {
        return new JavaFileWalker(projectPath, excludedPaths)
                .walk();
    }

    public Stream<Path> walk() {
        return walkDirectory(projectPath);
    }

    private Stream<Path> walkDirectory(Path dir) {
        if (excludedPaths.contains(projectPath.relativize(dir))) {
            logger.info("Skipping directory: {}", dir);
            return Stream.empty();
        }

        Map<Boolean, List<Path>> files;
        try {
            // TODO don't know how this will react to links
            files = Files.list(dir)
                    .collect(Collectors.groupingBy(Files::isDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Stream.concat(
                files.getOrDefault(false, Collections.emptyList()).stream()
                        .filter(p -> p.getFileName().toString().endsWith(".java")),
                files.getOrDefault(true, Collections.emptyList()).stream()
                        .flatMap(this::walkDirectory)
        );
    }
}
