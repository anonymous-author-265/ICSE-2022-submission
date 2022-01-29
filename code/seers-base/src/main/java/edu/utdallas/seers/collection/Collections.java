package edu.utdallas.seers.collection;

import edu.utdallas.seers.stream.PairSeq;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Collections {
    private Collections() {
    }

    public static <T1, T2> PairSeq<T1, T2> streamMap(Map<T1, T2> map) {
        return PairSeq.seq(map.entrySet(), Map.Entry::getKey, Map.Entry::getValue);
    }

    public static <T1, T2, R1, R2> Map<R1, R2> transformMap(
            Map<T1, T2> map,
            Function<? super T1, ? extends R1> firstMapper,
            Function<? super T2, ? extends R2> secondMapper
    ) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> firstMapper.apply(e.getKey()),
                        e -> secondMapper.apply(e.getValue())
                ));
    }

}
