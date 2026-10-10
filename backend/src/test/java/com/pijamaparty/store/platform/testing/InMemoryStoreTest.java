package com.pijamaparty.store.platform.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryStoreTest {

    private static final UUID ID = new UUID(0, 1);

    private InMemoryStore<UUID, Counter> store;

    @BeforeEach
    void createStore() {
        store = InMemoryStore.copying(Counter::id, Counter::copy);
    }

    @Test
    void givenALoadedAggregate_whenItChangesWithoutBeingSaved_thenTheStoreIsUnchanged() {
        store.save(new Counter(ID, 1));

        store.findById(ID).orElseThrow().increment();

        assertThat(store.findById(ID).orElseThrow().value()).isEqualTo(1);
    }

    @Test
    void givenASavedAggregate_whenTheCallerKeepsChangingItsInstance_thenTheStoreIsUnchanged() {
        Counter counter = new Counter(ID, 1);
        store.save(counter);

        counter.increment();

        assertThat(store.findById(ID).orElseThrow().value()).isEqualTo(1);
    }

    @Test
    void givenALoadedAggregate_whenItChangesAndIsSaved_thenTheStoreKeepsTheChange() {
        store.save(new Counter(ID, 1));
        Counter loaded = store.findById(ID).orElseThrow();

        loaded.increment();
        store.save(loaded);

        assertThat(store.findById(ID).orElseThrow().value()).isEqualTo(2);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void givenSeveralRows_whenFilteringAndDeleting_thenOnlyTheMatchingRowsAreAffected() {
        store.save(new Counter(new UUID(0, 1), 1));
        store.save(new Counter(new UUID(0, 2), 5));

        assertThat(store.findAll(counter -> counter.value() > 2)).extracting(Counter::value).containsExactly(5);
        assertThat(store.delete(new UUID(0, 1))).isTrue();
        assertThat(store.delete(new UUID(0, 1))).isFalse();
        assertThat(store.findAll()).hasSize(1);
    }

    @Test
    void givenAnUnknownId_whenLoading_thenItIsEmpty() {
        assertThat(store.findById(ID)).isEmpty();
    }

    /** Stand-in for a mutable aggregate. */
    private static final class Counter {
        private final UUID id;
        private int value;

        Counter(UUID id, int value) {
            this.id = id;
            this.value = value;
        }

        UUID id() {
            return id;
        }

        int value() {
            return value;
        }

        void increment() {
            value++;
        }

        Counter copy() {
            return new Counter(id, value);
        }
    }
}
