package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.platform.web.ratelimit.PeekableRequest;
import com.pijamaparty.store.platform.web.ratelimit.PeekableRequests;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** RNF-29.5: the account key of the login policy. */
class SignInEmailKeyResolverTest {

    private final RateLimitKeyResolver resolver = new SignInEmailKeyResolver(new ObjectMapper());

    @Test
    void givenTheSameEmailWrittenDifferently_whenResolved_thenItIsTheSameAccount() {
        String key = resolve("{\"email\":\"owner@example.com\",\"password\":\"x\"}");

        assertThat(resolve("{\"email\":\"  Owner@Example.COM \",\"password\":\"y\"}")).isEqualTo(key);
        assertThat(resolve("{\"email\":\"other@example.com\",\"password\":\"x\"}")).isNotEqualTo(key);
    }

    @Test
    void givenAnEmail_whenResolved_thenTheKeyDoesNotHoldItInClear() {
        assertThat(resolve("{\"email\":\"owner@example.com\"}")).doesNotContain("owner").hasSize(64);
    }

    @Test
    void givenBodiesWithoutAUsableEmail_whenResolved_thenTheyShareOneBucketInsteadOfEscapingTheLimit() {
        List<String> bodies = List.of("", "not json", "{}", "{\"email\":42}", "{\"email\":\"no-at-sign\"}",
            "{\"email\":\"a@b.c\",\"pad\":\"" + "x".repeat(PeekableRequest.MAX_PEEK_BYTES) + "\"}");

        for (String body : bodies) {
            assertThat(resolve(body)).as(body.length() > 40 ? "oversized body" : body)
                .isEqualTo(SignInEmailKeyResolver.UNREADABLE);
        }
    }

    /** Wrapped as the rate-limit filter wraps it in production. */
    private String resolve(String body) {
        var request = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return resolver.resolve(PeekableRequests.asFiltered(request)).orElseThrow();
    }
}
