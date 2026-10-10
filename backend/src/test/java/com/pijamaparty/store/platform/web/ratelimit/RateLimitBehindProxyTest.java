package com.pijamaparty.store.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

/**
 * RNF-22 behind the trusted proxy: with the default internal proxies the test client (127.0.0.1) plays Caddy, so
 * the {@code X-Forwarded-For} it sets is the real client IP and each client gets its own bucket.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, RateLimitTestRoutes.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RateLimitBehindProxyTest {

    private final TestRestTemplate http;

    RateLimitBehindProxyTest(TestRestTemplate http) {
        this.http = http;
    }

    @DynamicPropertySource
    static void policies(DynamicPropertyRegistry registry) {
        for (String property : RateLimitTestRoutes.POLICIES) {
            String[] pair = property.split("=", 2);
            registry.add(pair[0], () -> pair[1]);
        }
    }

    @Test
    void givenTwoClientsBehindTheProxy_whenOneExceedsTheLimit_thenTheOtherIsStillServed() {
        for (int i = 0; i < 3; i++) {
            assertThat(get("198.51.100.1").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertThat(get("198.51.100.1").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(get("198.51.100.2").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private org.springframework.http.ResponseEntity<String> get(String clientIp) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        return http.exchange("/api/test/public/limited", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
