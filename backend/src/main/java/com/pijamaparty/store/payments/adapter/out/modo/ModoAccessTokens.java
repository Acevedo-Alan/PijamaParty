package com.pijamaparty.store.payments.adapter.out.modo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * UC-14 (T-E3b-02): MODO's access token, asked with {@code POST /v2/stores/companies/token} and kept in memory until
 * an hour before it expires (it lasts a week). MODO allows 10 token requests every 10 minutes, so after a failed
 * request no other is made for a minute, and a token MODO refused is renewed once, however many requests saw it
 * refused. Never logs the credentials or the token.
 */
class ModoAccessTokens {

    static final String TOKEN_PATH = "/v2/stores/companies/token";

    /** The token is renewed this long before MODO says it expires. */
    static final Duration RENEWAL_MARGIN = Duration.ofHours(1);
    /** Never kept for less, so a short {@code expires_in} cannot spend MODO's token requests. */
    static final Duration MIN_LIFETIME = Duration.ofMinutes(5);
    /** After a failed token request, the next one waits this long. */
    static final Duration FAILURE_PAUSE = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(ModoAccessTokens.class);

    private final RestClient client;
    private final String username;
    private final String password;
    private final Clock clock;

    private String token;
    private Instant renewAt = Instant.MIN;
    private Instant nextRequestAllowedAt = Instant.MIN;

    ModoAccessTokens(RestClient client, String username, String password, Clock clock) {
        this.client = client;
        this.username = username;
        this.password = password;
        this.clock = clock;
    }

    /** The token in force, asked to MODO when there is none or it is about to expire; empty when MODO gave none. */
    synchronized Optional<String> current() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(renewAt)) {
            return Optional.of(token);
        }
        return request(now);
    }

    /**
     * After MODO refused {@code refused} with a {@code 401}: a new token, asked once. A request that saw a token
     * already renewed gets the new one without asking again.
     */
    synchronized Optional<String> renew(String refused) {
        if (token != null && !token.equals(refused)) {
            return Optional.of(token);
        }
        token = null;
        return request(clock.instant());
    }

    private Optional<String> request(Instant now) {
        if (now.isBefore(nextRequestAllowedAt)) {
            return Optional.empty();
        }
        TokenResponse response;
        try {
            response = client.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new TokenRequest(username, password))
                .retrieve()
                .body(TokenResponse.class);
        } catch (RestClientResponseException refused) {
            log.warn("MODO refused the access token request: HTTP {}", refused.getStatusCode().value());
            return failed(now);
        } catch (RestClientException unreachable) {
            log.warn("MODO did not answer the access token request: {}", unreachable.getClass().getSimpleName());
            return failed(now);
        }
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
            || response.expiresIn() == null) {
            log.warn("MODO answered the access token request without an access_token or an expires_in");
            return failed(now);
        }
        Duration kept = Duration.ofSeconds(response.expiresIn()).minus(RENEWAL_MARGIN);
        token = response.accessToken();
        renewAt = now.plus(kept.compareTo(MIN_LIFETIME) < 0 ? MIN_LIFETIME : kept);
        return Optional.of(token);
    }

    private Optional<String> failed(Instant now) {
        token = null;
        nextRequestAllowedAt = now.plus(FAILURE_PAUSE);
        return Optional.empty();
    }

    record TokenRequest(String username, String password) {

        @Override
        public String toString() {
            return "TokenRequest[***]";
        }
    }

    /** {@code expires_in} in seconds. The camel-case names are accepted too until preproduction confirms them. */
    record TokenResponse(@JsonProperty("access_token") @JsonAlias("accessToken") String accessToken,
                         @JsonProperty("expires_in") @JsonAlias("expiresIn") Long expiresIn) {

        @Override
        public String toString() {
            return "TokenResponse[***]";
        }
    }
}
