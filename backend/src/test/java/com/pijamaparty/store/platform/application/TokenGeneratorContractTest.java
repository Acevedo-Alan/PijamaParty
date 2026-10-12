package com.pijamaparty.store.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** What every {@link TokenGenerator} guarantees. */
public abstract class TokenGeneratorContractTest extends ContractTest<TokenGenerator> {

    @Test
    void givenAGenerator_whenAskingForAToken_thenItHas43Base64UrlCharacters() {
        assertThat(subject.newToken()).matches("[A-Za-z0-9_-]{" + TokenGenerator.LENGTH + "}");
    }

    @Test
    void givenConcurrentCallers_whenAskingForTokens_thenTheyAreAllDifferent() throws InterruptedException {
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            IntStream.range(0, 1_000).forEach(i -> pool.execute(() -> tokens.add(subject.newToken())));
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(tokens).hasSize(1_000);
    }
}
