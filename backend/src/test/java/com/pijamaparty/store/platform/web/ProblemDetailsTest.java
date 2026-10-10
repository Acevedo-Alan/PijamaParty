package com.pijamaparty.store.platform.web;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;

/**
 * E0: "Given an invalid request to the API, when it answers with an error, then the body is problem+json".
 * Real HTTP through the whole filter chain, as the frontend sees it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ProblemDetailsTestRoutes.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsTest {

    private final TestRestTemplate http;

    ProblemDetailsTest(TestRestTemplate http) {
        this.http = http;
    }

    @Test
    void givenAnInvalidRequest_whenTheApiAnswersWithAnError_thenTheBodyIsProblemJson() {
        ResponseEntity<String> response = postGreeting("{\"name\":\"\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertProblemJson(response, 400);
    }

    @Test
    void givenAMalformedBody_whenTheApiAnswers_thenTheBodyIsProblemJson() {
        ResponseEntity<String> response = postGreeting("{not json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertProblemJson(response, 400);
    }

    @Test
    void givenAnUnknownPublicRoute_whenRequested_thenItIsA404InProblemJson() {
        ResponseEntity<String> response = http.getForEntity("/api/test/public/missing", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertProblemJson(response, 404);
    }

    @Test
    void givenAnUnexpectedFailure_whenTheApiAnswers_thenItIsAGeneric500WithoutInternalsOrPersonalData(
        CapturedOutput output) {
        ResponseEntity<String> response = http.getForEntity("/api/test/public/failure", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertProblemJson(response, 500);
        assertThat(response.getBody()).doesNotContain("ana@gmail.com", "30123456", "select", "IllegalStateException");
        assertThat(output).contains("java.lang.IllegalStateException").doesNotContain("ana@gmail.com", "30123456");
    }

    @Test
    void givenAnAccessDeniedInsideAController_whenTheApiAnswers_thenItIsA403NotA500() {
        ResponseEntity<String> response = http.getForEntity("/api/test/public/denied", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertProblemJson(response, 403);
    }

    @Test
    void givenAnErrorResponse_whenAnonymous_thenNoSessionIsCreated() {
        ResponseEntity<String> response = http.getForEntity("/api/test/public/failure", String.class);

        // The only cookie an anonymous visitor gets is the CSRF token: no session of any name.
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).allMatch(cookie -> cookie.startsWith("XSRF-TOKEN="));
    }

    @Test
    void givenARequestRejectedOutsideSpringMvc_whenTheApiAnswers_thenTheBodyIsProblemJsonWithoutThePath() {
        ResponseEntity<String> response = http.getForEntity("/api/health;evil=1", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertProblemJson(response, 400);
        assertThat(response.getBody()).doesNotContain("evil", "/api/health", "timestamp");
    }

    @Test
    void givenAValidRequest_whenSent_thenItSucceeds() {
        ResponseEntity<String> response = postGreeting("{\"name\":\"Ana\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"name\":\"Ana\"");
    }

    /** Posts like the SPA does: the CSRF token from the cookie goes back raw in the header. */
    private ResponseEntity<String> postGreeting(String json) {
        String csrfCookie = http.getForEntity("/api/health", String.class).getHeaders()
            .get(HttpHeaders.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        String token = csrfCookie.substring("XSRF-TOKEN=".length(), csrfCookie.indexOf(';'));

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token);
        headers.add("X-XSRF-TOKEN", token);
        return http.postForEntity("/api/test/public/greetings", new HttpEntity<>(json, headers), String.class);
    }

    private static void assertProblemJson(ResponseEntity<String> response, int status) {
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"status\":" + status).contains("\"title\"");
    }
}
