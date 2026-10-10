package com.pijamaparty.store.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** What every {@link IdGenerator} guarantees, the fake included. */
public abstract class IdGeneratorContractTest extends ContractTest<IdGenerator> {

    @Test
    void givenAGenerator_whenAskingForAnId_thenItReturnsOne() {
        assertThat(subject.newId()).isNotNull();
    }

    @Test
    void givenAGenerator_whenAskingForManyIds_thenTheyAreAllDifferent() {
        var ids = IntStream.range(0, 1_000).mapToObj(i -> subject.newId()).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void givenConcurrentCallers_whenAskingForIds_thenTheyAreAllDifferent() throws InterruptedException {
        Set<UUID> ids = ConcurrentHashMap.newKeySet();
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            IntStream.range(0, 1_000).forEach(i -> pool.execute(() -> ids.add(subject.newId())));
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(ids).hasSize(1_000);
    }
}
