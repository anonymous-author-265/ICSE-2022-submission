package edu.utdallas.seers.stream;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.*;
import java.util.stream.Stream;

import static org.jooq.lambda.tuple.Tuple.tuple;

public class PairSeq<T1, T2> extends TupleSeq<Tuple2<T1, T2>, BiPredicate<T1, T2>, BiConsumer<T1, T2>, PairSeq<T1, T2>> {
    PairSeq(Seq<Tuple2<T1, T2>> delegate) {
        super(delegate);
    }

    public static <U, T> PairSeq<U, Seq<T>> grouped(Collection<T> items, Function<? super T, ? extends U> classifier) {
        return new PairSeq<>(
                Seq.seq(items)
                        .grouped(classifier)
        );
    }

    public static <U, T1, T2> PairSeq<T1, T2> seq(
            Seq<U> stream,
            Function<? super U, ? extends T1> firstMapper,
            Function<? super U, ? extends T2> secondMapper
    ) {
        return new PairSeq<>(
                stream.map(u -> tuple(firstMapper.apply(u), secondMapper.apply(u)))
        );
    }

    public static <U, T1, T2> PairSeq<T1, T2> seq(
            Stream<U> stream,
            Function<? super U, ? extends T1> firstMapper,
            Function<? super U, ? extends T2> secondMapper
    ) {
        return new PairSeq<>(
                Seq.seq(stream).map(u -> tuple(firstMapper.apply(u), secondMapper.apply(u)))
        );
    }

    public static <U, T1, T2> PairSeq<T1, T2> seq(Collection<U> items,
                                                  Function<? super U, ? extends T1> firstMapper,
                                                  Function<? super U, ? extends T2> secondMapper) {
        return seq(Seq.seq(items), firstMapper, secondMapper);
    }

    public static <T1, T2> PairSeq<T1, T2> seq(Stream<Tuple2<T1, T2>> items) {
        return new PairSeq<>(Seq.seq(items));
    }

    public static <T1, T2> PairSeq<T1, T2> seq(Collection<Tuple2<T1, T2>> items) {
        return new PairSeq<>(Seq.seq(items));
    }

    public static <T> PairSeq<T, Long> zipWithIndex(Stream<T> items) {
        return zipWithIndex(Seq.seq(items));
    }

    public static <T> PairSeq<T, Long> zipWithIndex(Seq<T> seq) {
        return new PairSeq<>(
                Seq.zipWithIndex(seq)
        );
    }

    public static <T> PairSeq<T, Long> zipWithIndex(T[] items) {
        return zipWithIndex(Seq.seq(Arrays.stream(items)));
    }

    public static <T> PairSeq<T, Long> zipWithIndex(Collection<T> items) {
        return zipWithIndex(Seq.seq(items));
    }

    public <K, V> Map<K, V> toMapByPair(Function<? super T1, ? extends K> keyMapper, Function<? super T2, ? extends V> valueMapper) {
        return super.toMap(t -> keyMapper.apply(t.v1), t -> valueMapper.apply(t.v2));
    }

    public <R> Seq<R> combine(BiFunction<? super T1, ? super T2, ? extends R> mapper) {
        return super.map(p -> p.map(mapper));
    }

    public <R> Seq<R> flatMap(BiFunction<? super T1, ? super T2, Stream<? extends R>> mapper) {
        return super.flatMap(p -> p.map(mapper));
    }

    public <R1, R2> PairSeq<R1, R2>
    map(Function<? super T1, ? extends R1> firstMapper, Function<? super T2, ? extends R2> secondMapper) {
        return new PairSeq<>(
                super.map(p -> tuple(firstMapper.apply(p.v1), secondMapper.apply(p.v2)))
        );
    }

    public Map<T1, T2> toMap() {
        return Seq.toMap(this);
    }

    public <R extends Comparable<? super R>>
    PairSeq<T1, T2> sortedComparing(BiFunction<? super T1, ? super T2, ? extends R> keyExtractor) {
        return new PairSeq<>(
                super.sorted(Comparator.comparing(p -> p.map(keyExtractor)))
        );
    }

    public <R extends Comparable<? super R>>
    PairSeq<T1, T2> sortedComparingReversed(BiFunction<? super T1, ? super T2, ? extends R> keyExtractor) {
        Comparator<Tuple2<T1, T2>> comparator = Comparator.comparing(p -> p.map(keyExtractor));
        return new PairSeq<>(
                super.sorted(comparator.reversed())
        );
    }

    public <R1, R2> PairSeq<R1, R2>
    flatCombine(BiFunction<? super T1, ? super T2, Stream<Tuple2<R1, R2>>> transformer) {
        return new PairSeq<>(
                super.flatMap(p -> p.map(transformer))
        );
    }

    public <K, S> PairSeq<K, Seq<S>> grouped(BiFunction<? super T1, ? super T2, ? extends K> classifier,
                                             BiFunction<? super T1, ? super T2, ? extends S> extractor) {
        return new PairSeq<>(
                super.grouped(t -> t.map(classifier))
        )
                .map(k -> k, vs -> vs.map(t -> t.map(extractor)));
    }


    @Override
    protected PairSeq<T1, T2> wrap(Seq<Tuple2<T1, T2>> seq) {
        return new PairSeq<>(seq);
    }

    @Override
    protected Predicate<? super Tuple2<T1, T2>> makePredicate(BiPredicate<T1, T2> predicate) {
        return t -> predicate.test(t.v1, t.v2);
    }

    @Override
    protected Consumer<? super Tuple2<T1, T2>> makeConsumer(BiConsumer<T1, T2> consumer) {
        return t -> consumer.accept(t.v1, t.v2);
    }
}
