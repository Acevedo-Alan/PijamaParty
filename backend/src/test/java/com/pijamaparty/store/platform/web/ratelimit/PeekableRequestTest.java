package com.pijamaparty.store.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PeekableRequestTest {

    @Test
    void givenASmallBody_whenPeeked_thenTheControllerStillReadsAllOfIt() throws IOException {
        var request = new PeekableRequest(post("{\"email\":\"a@b.c\"}"));

        assertThat(PeekableRequest.body(request)).hasValueSatisfying(body ->
            assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("{\"email\":\"a@b.c\"}"));
        assertThat(PeekableRequest.body(request)).as("peeking twice gives the same body").isPresent();
        assertThat(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .isEqualTo("{\"email\":\"a@b.c\"}");
    }

    @Test
    void givenABodyOverTheLimit_whenPeeked_thenItIsNotGivenButTheControllerStillReadsAllOfIt() throws IOException {
        String large = "x".repeat(PeekableRequest.MAX_PEEK_BYTES + 100);
        var request = new PeekableRequest(post(large));

        assertThat(PeekableRequest.body(request)).isEmpty();
        assertThat(request.getReader().lines().reduce("", String::concat)).isEqualTo(large);
    }

    @Test
    void givenABodyAlreadyRead_whenPeeked_thenItIsEmpty() throws IOException {
        var request = new PeekableRequest(post("{}"));
        request.getInputStream().readAllBytes();

        assertThat(PeekableRequest.body(request)).isEmpty();
    }

    @Test
    void givenARequestThatDidNotGoThroughTheFilter_whenPeeked_thenItIsEmpty() throws IOException {
        assertThat(PeekableRequest.body(post("{}"))).isEmpty();
    }

    private static MockHttpServletRequest post(String body) {
        var request = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
