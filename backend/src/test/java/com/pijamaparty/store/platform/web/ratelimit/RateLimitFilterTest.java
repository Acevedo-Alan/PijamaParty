package com.pijamaparty.store.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

/**
 * RNF-22 through real HTTP. Here the test client (127.0.0.1) is NOT a trusted proxy, like an attacker reaching the
 * app directly: whatever {@code X-Forwarded-For} it sends, the limit applies to its real address.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.254")
@Import({TestcontainersConfiguration.class, RateLimitTestRoutes.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class RateLimitFilterTest {

    private final TestRestTemplate http;

    RateLimitFilterTest(TestRestTemplate http) {
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
    void givenAClientOverTheLimitWithForgedIps_whenItSendsAnotherRequest_thenItGets429WithRetryAfterInProblemJson(
        CapturedOutput output) {
        for (int i = 1; i <= 3; i++) {
            assertThat(get("/api/test/public/limited", "203.0.113." + i).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> limited = get("/api/test/public/limited", "203.0.113.99");

        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(limited.getBody()).contains("\"status\":429");
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);
        assertThat(output).contains("policy=limited", "route=/api/test/public/limited")
            .doesNotContain("127.0.0.1", "203.0.113");
    }

    @Test
    void givenAnExcludedPath_whenCalledManyTimes_thenItIsNeverLimited() {
        for (int i = 0; i < 10; i++) {
            assertThat(http.getForEntity("/api/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private ResponseEntity<String> get(String path, String forwardedFor) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", forwardedFor);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
