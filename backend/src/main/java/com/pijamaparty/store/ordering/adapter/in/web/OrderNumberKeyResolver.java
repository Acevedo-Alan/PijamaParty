package com.pijamaparty.store.ordering.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.ordering.domain.OrderNumberText;
import com.pijamaparty.store.platform.web.ratelimit.PeekableRequest;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * The order of a withdrawal request, for the policy of UC-29 (RNF-22 "Pedido + IP"): the body's {@code orderNumber},
 * read as the use case reads it, so {@code 1001} and {@code #1001} share a bucket. It does not look the order up, so
 * the {@code 429} never reveals whether it exists. Order numbers are no secret.
 *
 * <p>A body without a usable number (too large, not JSON, no number) shares one bucket instead of escaping the limit:
 * the storefront never sends one, so only such requests compete for it.
 */
@Component
class OrderNumberKeyResolver implements RateLimitKeyResolver {

    static final String UNREADABLE = "unreadable";

    private final ObjectMapper objectMapper;

    OrderNumberKeyResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "order-number";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        OptionalLong number = number(request);
        return Optional.of(number.isPresent() ? Long.toString(number.getAsLong()) : UNREADABLE);
    }

    private OptionalLong number(HttpServletRequest request) {
        try {
            Optional<byte[]> body = PeekableRequest.body(request);
            if (body.isEmpty()) {
                return OptionalLong.empty();
            }
            JsonNode number = objectMapper.readTree(body.get()).path("orderNumber");
            return number.isTextual() ? OrderNumberText.parse(number.textValue()) : OptionalLong.empty();
        } catch (IOException notReadable) {
            return OptionalLong.empty();
        }
    }
}
