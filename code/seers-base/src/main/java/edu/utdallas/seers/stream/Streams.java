package edu.utdallas.seers.stream;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Streams {
    private Streams() {
    }

    public static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
