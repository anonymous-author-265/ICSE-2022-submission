package edu.utdallas.seers.parameter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class StorePath extends StoreSingleSilent<String, Path> {
    public StorePath(Consumer<Path> setter) {
        super(setter, String.class);
    }

    @Override
    protected Path transform(String value) {
        return Paths.get(value);
    }
}
