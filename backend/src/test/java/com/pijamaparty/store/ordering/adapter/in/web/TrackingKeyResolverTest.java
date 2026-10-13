package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** T-E3-08: the order key of the payment-retry policy (UC-14). */
class TrackingKeyResolverTest {

    private static final String TOKEN = "a".repeat(43);

    private final RateLimitKeyResolver resolver = new TrackingKeyResolver();

    @Test
    void givenTheSameTrackingToken_whenResolved_thenItIsTheSameOrderWithoutTheTokenInClear() {
        String key = resolve("/api/orders/" + TOKEN + "/retry-payment").orElseThrow();

        assertThat(key).isEqualTo(resolve("/api/orders/" + TOKEN).orElseThrow()).doesNotContain(TOKEN).hasSize(64);
        assertThat(resolve("/api/orders/" + "b".repeat(43) + "/retry-payment")).isNotEqualTo(Optional.of(key));
    }

    @Test
    void givenAPathWithoutAWellFormedToken_whenResolved_thenThereIsNoOrder() {
        assertThat(resolve("/api/orders/too-short/retry-payment")).isEmpty();
        assertThat(resolve("/api/orders/" + "a".repeat(44) + "/retry-payment")).isEmpty();
        assertThat(resolve("/api/checkout")).isEmpty();
    }

    private Optional<String> resolve(String path) {
        return resolver.resolve(new MockHttpServletRequest("POST", path));
    }
}
