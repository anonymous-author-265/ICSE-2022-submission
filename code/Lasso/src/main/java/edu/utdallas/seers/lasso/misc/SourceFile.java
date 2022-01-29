package edu.utdallas.seers.lasso.misc;

import edu.utdallas.seers.retrieval.Retrievable;

import java.nio.file.Path;

public class SourceFile implements Retrievable {
    private final Path path;
    private final String packagePath;

    public SourceFile(Path path, String packagePath) {
        this.path = path;
        this.packagePath = packagePath;
    }

    @Override
    public String getID() {
        return null;
    }

    public Path getPath() {
        return path;
    }

    public String getPackagePath() {
        return packagePath;
    }
}
