package com.pijamaparty.store.platform.testing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Storage behind {@code InMemory<Port>} fakes. It keeps and hands out <em>copies</em>, like a database does: a use
 * case that changes a loaded aggregate but forgets to save it leaves the store unchanged, so the fake fails the same
 * way the real adapter would. Thread-safe, for concurrency tests.
 *
 * @param <K> the id type
 * @param <V> the stored aggregate or row
 */
public final class InMemoryStore<K, V> {

    private final Map<K, V> rows = new ConcurrentHashMap<>();
    private final Function<V, K> idOf;
    private final UnaryOperator<V> copy;

    private InMemoryStore(Function<V, K> idOf, UnaryOperator<V> copy) {
        this.idOf = Objects.requireNonNull(idOf, "idOf");
        this.copy = Objects.requireNonNull(copy, "copy");
    }

    /** For mutable aggregates: {@code copy} must return a deep, independent copy. */
    public static <K, V> InMemoryStore<K, V> copying(Function<V, K> idOf, UnaryOperator<V> copy) {
        return new InMemoryStore<>(idOf, copy);
    }

    /** For immutable values (records of immutable fields), which need no copy. */
    public static <K, V> InMemoryStore<K, V> ofImmutable(Function<V, K> idOf) {
        return new InMemoryStore<>(idOf, UnaryOperator.identity());
    }

    /** Inserts or replaces by id. */
    public void save(V value) {
        rows.put(idOf.apply(value), copy.apply(value));
    }

    public Optional<V> findById(K id) {
        return Optional.ofNullable(rows.get(id)).map(copy);
    }

    public List<V> findAll() {
        return findAll(value -> true);
    }

    public List<V> findAll(Predicate<? super V> filter) {
        return rows.values().stream().filter(filter).map(copy).toList();
    }

    /** Returns whether a row was removed. */
    public boolean delete(K id) {
        return rows.remove(id) != null;
    }

    public int size() {
        return rows.size();
    }

    public void clear() {
        rows.clear();
    }
}
