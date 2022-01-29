package edu.utdallas.seers.stream;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Extension of jooL's {@link Seq} to operate more easily on Seqs where elements are tuples.
 * Must be implemented through delegation because the default Seq implementation is package-private.
 * <p>
 * Uses generics tricks to allow maximum code re-use in subclasses. For example, the subclass needs
 * to define a predicate type (e.g. {@link BiPredicate}, {@link org.jooq.lambda.function.Function4}
 * with a return of Boolean, etc) that will be used for overloads of methods such as
 * {@link TupleSeq#filter(Object)}, that allow them to take a "decomposed" tuple as opposed to
 * the entire tuple object. The subclass only needs to provide a method
 * {@link TupleSeq#makePredicate(Object)} that turns the specific predicate type into
 * a {@link Predicate}.
 * <p>
 * Similarly, a consumer type should be defined with its corresponding method
 * {@link TupleSeq#makeConsumer(Object)}. Finally, the {@link TupleSeq#wrap(Seq)} method
 * should be implemented to return instances of the subclass in overloads such as
 * {@link TupleSeq#sorted()}, so that we don't return a plain Seq.
 *
 * @param <T> Type of Tuple elements of this Seq.
 * @param <P> Type of predicate to use in convenience methods.
 * @param <C> Type of consumer to use in convenience methods.
 * @param <S> Type of subclass. Must be the same as the implementing subclass.
 */
@SuppressWarnings("unused")
abstract class TupleSeq<T extends Tuple, P, C, S extends TupleSeq<T, P, C, S>> implements Seq<T> {
    private final Seq<T> delegate;

    TupleSeq(Seq<T> delegate) {
        this.delegate = delegate;
    }

    public S filter(P predicate) {
        return wrap(delegate.filter(makePredicate(predicate)));
    }

    protected abstract S wrap(Seq<T> seq);

    protected abstract Predicate<? super T> makePredicate(P predicate);

    public void forEachOrdered(C consumer) {
        delegate.forEachOrdered(makeConsumer(consumer));
    }

    protected abstract Consumer<? super T> makeConsumer(C consumer);

    public long count(P predicate) {
        return delegate.count(makePredicate(predicate));
    }

    public long countDistinct(P predicate) {
        return delegate.countDistinct(makePredicate(predicate));
    }

    public boolean anyMatch(P predicate) {
        return delegate.anyMatch(makePredicate(predicate));
    }

    public boolean allMatch(P predicate) {
        return delegate.allMatch(makePredicate(predicate));
    }

    public boolean noneMatch(P predicate) {
        return delegate.noneMatch(makePredicate(predicate));
    }

    @Override
    public <K> PairSeq<K, Seq<T>> grouped(Function<? super T, ? extends K> classifier) {
        return new PairSeq<>(Seq.super.grouped(classifier));
    }

    @Override
    public <K, A, D> PairSeq<K, D> grouped(Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream) {
        return new PairSeq<>(Seq.super.grouped(classifier, downstream));
    }

    @Override
    public Stream<T> stream() {
        return delegate.stream();
    }

    @Override
    public Seq<T> filter(Predicate<? super T> predicate) {
        return delegate.filter(predicate);
    }

    @Override
    public <R> Seq<R> map(Function<? super T, ? extends R> mapper) {
        return delegate.map(mapper);
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return delegate.mapToInt(mapper);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return delegate.mapToLong(mapper);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return delegate.mapToDouble(mapper);
    }

    @Override
    public <R> Seq<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return delegate.flatMap(mapper);
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return delegate.flatMapToInt(mapper);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return delegate.flatMapToLong(mapper);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return delegate.flatMapToDouble(mapper);
    }

    @Override
    public S distinct() {
        return wrap(delegate.distinct());
    }

    @Override
    public S sorted() {
        return wrap(delegate.sorted());
    }

    @Override
    public S sorted(Comparator<? super T> comparator) {
        return wrap(delegate.sorted(comparator));
    }

    @Override
    public S peek(Consumer<? super T> action) {
        return wrap(delegate.peek(action));
    }

    @Override
    public S limit(long maxSize) {
        return wrap(delegate.limit(maxSize));
    }

    @Override
    public S skip(long n) {
        return wrap(delegate.skip(n));
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        delegate.forEachOrdered(action);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @SuppressWarnings("SuspiciousToArrayCall")
    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return delegate.toArray(generator);
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return delegate.reduce(identity, accumulator);
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return delegate.reduce(accumulator);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return delegate.reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        return delegate.collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return delegate.collect(collector);
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return delegate.min(comparator);
    }

    @Override
    public <U extends Comparable<? super U>> Optional<U> min(Function<? super T, ? extends U> function) {
        return delegate.min(function);
    }

    @Override
    public <U> Optional<U> min(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.min(function, comparator);
    }

    @Override
    public <U extends Comparable<? super U>> Optional<T> minBy(Function<? super T, ? extends U> function) {
        return delegate.minBy(function);
    }

    @Override
    public <U> Optional<T> minBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.minBy(function, comparator);
    }

    @Override
    public S minAll() {
        return wrap(delegate.minAll());
    }

    @Override
    public S minAll(Comparator<? super T> comparator) {
        return wrap(delegate.minAll(comparator));
    }

    @Override
    public <U extends Comparable<? super U>> Seq<U> minAll(Function<? super T, ? extends U> function) {
        return delegate.minAll(function);
    }

    @Override
    public <U> Seq<U> minAll(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.minAll(function, comparator);
    }

    @Override
    public <U extends Comparable<? super U>> S minAllBy(Function<? super T, ? extends U> function) {
        return wrap(delegate.minAllBy(function));
    }

    @Override
    public <U> S minAllBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return wrap(delegate.minAllBy(function, comparator));
    }

    @Override
    public Optional<T> max() {
        return delegate.max();
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return delegate.max(comparator);
    }

    @Override
    public <U extends Comparable<? super U>> Optional<U> max(Function<? super T, ? extends U> function) {
        return delegate.max(function);
    }

    @Override
    public <U> Optional<U> max(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.max(function, comparator);
    }

    @Override
    public <U extends Comparable<? super U>> Optional<T> maxBy(Function<? super T, ? extends U> function) {
        return delegate.maxBy(function);
    }

    @Override
    public <U> Optional<T> maxBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.maxBy(function, comparator);
    }

    @Override
    public S maxAll() {
        return wrap(delegate.maxAll());
    }

    @Override
    public S maxAll(Comparator<? super T> comparator) {
        return wrap(delegate.maxAll(comparator));
    }

    @Override
    public <U extends Comparable<? super U>> Seq<U> maxAll(Function<? super T, ? extends U> function) {
        return delegate.maxAll(function);
    }

    @Override
    public <U> Seq<U> maxAll(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.maxAll(function, comparator);
    }

    @Override
    public <U extends Comparable<? super U>> S maxAllBy(Function<? super T, ? extends U> function) {
        return wrap(delegate.maxAllBy(function));
    }

    @Override
    public <U> S maxAllBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return wrap(delegate.maxAllBy(function, comparator));
    }

    @Override
    public Optional<T> median() {
        return delegate.median();
    }

    @Override
    public Optional<T> median(Comparator<? super T> comparator) {
        return delegate.median(comparator);
    }

    @Override
    public <U extends Comparable<? super U>> Optional<T> medianBy(Function<? super T, ? extends U> function) {
        return delegate.medianBy(function);
    }

    @Override
    public <U> Optional<T> medianBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.medianBy(function, comparator);
    }

    @Override
    public Optional<T> percentile(double percentile) {
        return delegate.percentile(percentile);
    }

    @Override
    public Optional<T> percentile(double percentile, Comparator<? super T> comparator) {
        return delegate.percentile(percentile, comparator);
    }

    @Override
    public <U extends Comparable<? super U>> Optional<T> percentileBy(double percentile, Function<? super T, ? extends U> function) {
        return delegate.percentileBy(percentile, function);
    }

    @Override
    public <U> Optional<T> percentileBy(double percentile, Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return delegate.percentileBy(percentile, function, comparator);
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public boolean isParallel() {
        return delegate.isParallel();
    }

    @Override
    public S onClose(Runnable closeHandler) {
        return wrap(delegate.onClose(closeHandler));
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public long count(Predicate<? super T> predicate) {
        return delegate.count(predicate);
    }

    @Override
    public long countDistinct() {
        return delegate.countDistinct();
    }

    @Override
    public long countDistinct(Predicate<? super T> predicate) {
        return delegate.countDistinct(predicate);
    }

    @Override
    public <U> long countDistinctBy(Function<? super T, ? extends U> function) {
        return delegate.countDistinctBy(function);
    }

    @Override
    public <U> long countDistinctBy(Function<? super T, ? extends U> function, Predicate<? super U> predicate) {
        return delegate.countDistinctBy(function, predicate);
    }

    @Override
    public Optional<T> mode() {
        return delegate.mode();
    }

    @Override
    public <U> Optional<T> modeBy(Function<? super T, ? extends U> function) {
        return delegate.modeBy(function);
    }

    @Override
    public S modeAll() {
        return wrap(delegate.modeAll());
    }

    @Override
    public <U> S modeAllBy(Function<? super T, ? extends U> function) {
        return wrap(delegate.modeAllBy(function));
    }

    @Override
    public Optional<T> sum() {
        return delegate.sum();
    }

    @Override
    public <U> Optional<U> sum(Function<? super T, ? extends U> function) {
        return delegate.sum(function);
    }

    @Override
    public int sumInt(ToIntFunction<? super T> function) {
        return delegate.sumInt(function);
    }

    @Override
    public long sumLong(ToLongFunction<? super T> function) {
        return delegate.sumLong(function);
    }

    @Override
    public double sumDouble(ToDoubleFunction<? super T> function) {
        return delegate.sumDouble(function);
    }

    @Override
    public Optional<T> avg() {
        return delegate.avg();
    }

    @Override
    public <U> Optional<U> avg(Function<? super T, ? extends U> function) {
        return delegate.avg(function);
    }

    @Override
    public double avgInt(ToIntFunction<? super T> function) {
        return delegate.avgInt(function);
    }

    @Override
    public double avgLong(ToLongFunction<? super T> function) {
        return delegate.avgLong(function);
    }

    @Override
    public double avgDouble(ToDoubleFunction<? super T> function) {
        return delegate.avgDouble(function);
    }

    @Override
    public Optional<T> min() {
        return delegate.min();
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return delegate.anyMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return delegate.allMatch(predicate);
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return delegate.noneMatch(predicate);
    }

    @Override
    public Optional<T> bitAnd() {
        return delegate.bitAnd();
    }

    @Override
    public <U> Optional<U> bitAnd(Function<? super T, ? extends U> function) {
        return delegate.bitAnd(function);
    }

    @Override
    public int bitAndInt(ToIntFunction<? super T> function) {
        return delegate.bitAndInt(function);
    }

    @Override
    public long bitAndLong(ToLongFunction<? super T> function) {
        return delegate.bitAndLong(function);
    }

    @Override
    public Optional<T> bitOr() {
        return delegate.bitOr();
    }

    @Override
    public <U> Optional<U> bitOr(Function<? super T, ? extends U> function) {
        return delegate.bitOr(function);
    }

    @Override
    public int bitOrInt(ToIntFunction<? super T> function) {
        return delegate.bitOrInt(function);
    }

    @Override
    public long bitOrLong(ToLongFunction<? super T> function) {
        return delegate.bitOrLong(function);
    }

    @Override
    public List<T> toList() {
        return delegate.toList();
    }

    @Override
    public <L extends List<T>> L toList(Supplier<L> factory) {
        return delegate.toList(factory);
    }

    @Override
    public List<T> toUnmodifiableList() {
        return delegate.toUnmodifiableList();
    }

    @Override
    public Set<T> toSet() {
        return delegate.toSet();
    }

    @Override
    public <Q extends Set<T>> Q toSet(Supplier<Q> factory) {
        return delegate.toSet(factory);
    }

    @Override
    public Set<T> toUnmodifiableSet() {
        return delegate.toUnmodifiableSet();
    }

    @Override
    public <Q extends Collection<T>> Q toCollection(Supplier<Q> factory) {
        return delegate.toCollection(factory);
    }

    @Override
    public <K, V> Map<K, V> toMap(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
        return delegate.toMap(keyMapper, valueMapper);
    }

    @Override
    public <K> Map<K, T> toMap(Function<? super T, ? extends K> keyMapper) {
        return delegate.toMap(keyMapper);
    }

    @Override
    public String toString(CharSequence delimiter) {
        return delegate.toString(delimiter);
    }

    @Override
    public String toString(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return delegate.toString(delimiter, prefix, suffix);
    }

    @Override
    public String commonPrefix() {
        return delegate.commonPrefix();
    }

    @Override
    public String commonSuffix() {
        return delegate.commonSuffix();
    }

    @Override
    public Optional<T> findFirst() {
        return delegate.findFirst();
    }

    @Override
    public Optional<T> findAny() {
        return delegate.findAny();
    }

    @Override
    public String format() {
        return delegate.format();
    }
}
