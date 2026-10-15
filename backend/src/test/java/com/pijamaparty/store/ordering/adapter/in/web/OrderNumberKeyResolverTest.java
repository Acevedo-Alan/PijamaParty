package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.platform.web.ratelimit.PeekableRequest;
import com.pijamaparty.store.platform.web.ratelimit.PeekableRequests;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** UC-29 (T-E3-25, RNF-22 "Pedido + IP"): the order key of the withdrawal policy. */
class OrderNumberKeyResolverTest {

    private final RateLimitKeyResolver resolver = new OrderNumberKeyResolver(new ObjectMapper());

    @Test
    void givenTheSameOrderNumberWrittenDifferently_whenResolved_thenItIsTheSameOrder() {
        String key = resolve("{\"orderNumber\":\"1001\",\"email\":\"ana@example.com\"}");

        assertThat(key).isEqualTo("1001");
        assertThat(resolve("{\"orderNumber\":\" #1001 \",\"email\":\"otra@example.com\"}")).isEqualTo(key);
        assertThat(resolve("{\"orderNumber\":\"1002\",\"email\":\"ana@example.com\"}")).isNotEqualTo(key);
    }

    @Test
    void givenBodiesWithoutAUsableNumber_whenResolved_thenTheyShareOneBucketInsteadOfEscapingTheLimit() {
        List<String> bodies = List.of("", "not json", "{}", "{\"orderNumber\":1001}", "{\"orderNumber\":\"abc\"}",
            "{\"orderNumber\":\"1001\",\"pad\":\"" + "x".repeat(PeekableRequest.MAX_PEEK_BYTES) + "\"}");

        for (String body : bodies) {
            assertThat(resolve(body)).as(body.length() > 40 ? "oversized body" : body)
                .isEqualTo(OrderNumberKeyResolver.UNREADABLE);
        }
    }

    /** Wrapped as the rate-limit filter wraps it in production. */
    private String resolve(String body) {
        var request = new MockHttpServletRequest("POST", "/api/withdrawals");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return resolver.resolve(PeekableRequests.asFiltered(request)).orElseThrow();
    }
}
