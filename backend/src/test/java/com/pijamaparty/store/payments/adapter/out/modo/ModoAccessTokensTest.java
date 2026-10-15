package com.pijamaparty.store.payments.adapter.out.modo;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.MovableClock;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** T-E3b-02: MODO's token is kept a week less an hour, and its 10 token requests every 10 minutes are spared. */
class ModoAccessTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

    private final ModoStub stub = new ModoStub();
    private final MovableClock clock = new MovableClock(NOW);
    private final ModoAccessTokens tokens = new ModoAccessTokens(ModoRestClients.create(new ModoProperties(true,
        false, stub.baseUrl(), URI.create("https://site.modo.com.ar"), "P-123", "1CSI", "PijamaParty",
        Duration.ofSeconds(1), Duration.ofSeconds(2))), ModoStub.USERNAME, ModoStub.PASSWORD, clock);

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenAToken_whenAskedAgainUntilAnHourBeforeItExpires_thenTheSameIsAnsweredWithoutAskingMODO() {
        assertThat(tokens.current()).contains("token-1");
        clock.advance(Duration.ofSeconds(ModoStub.TOKEN_LIFETIME_SECONDS).minus(Duration.ofHours(1)).minusSeconds(1));

        assertThat(tokens.current()).contains("token-1");
        assertThat(stub.requestsTo(ModoAccessTokens.TOKEN_PATH)).hasSize(1);
    }

    @Test
    void givenATokenAnHourFromExpiring_whenAskedAgain_thenANewOneIsAsked() {
        tokens.current();
        clock.advance(Duration.ofSeconds(ModoStub.TOKEN_LIFETIME_SECONDS).minus(Duration.ofHours(1)));

        assertThat(tokens.current()).contains("token-2");
    }

    @Test
    void givenATokenMODORefused_whenTwoRequestsRenewIt_thenMODOIsAskedOnce() {
        String refused = tokens.current().orElseThrow();

        assertThat(tokens.renew(refused)).contains("token-2");
        assertThat(tokens.renew(refused)).contains("token-2");
        assertThat(stub.requestsTo(ModoAccessTokens.TOKEN_PATH)).hasSize(2);
    }

    @Test
    void givenAFailedTokenRequest_whenAskedAgainWithinAMinute_thenMODOIsNotAskedUntilTheMinuteIsOver() {
        stub.answerTokens(503, "{\"message\":\"unavailable\"}");
        assertThat(tokens.current()).isEmpty();
        stub.answerTokens(200, "{\"access_token\":\"token-after\",\"expires_in\":604800}");

        clock.advance(Duration.ofSeconds(59));
        assertThat(tokens.current()).isEmpty();
        clock.advance(Duration.ofSeconds(1));
        assertThat(tokens.current()).contains("token-after");
        assertThat(stub.requestsTo(ModoAccessTokens.TOKEN_PATH)).hasSize(2);
    }

    @Test
    void givenAnAnswerWithoutExpiry_whenATokenIsAsked_thenThereIsNone() {
        stub.answerTokens(200, "{\"access_token\":\"token-x\"}");

        assertThat(tokens.current()).isEmpty();
    }

    @Test
    void givenAVeryShortExpiry_whenATokenIsAsked_thenItIsKeptFiveMinutesAnyway() {
        stub.answerTokens(200, "{\"access_token\":\"token-short\",\"expires_in\":60}");
        tokens.current();
        clock.advance(Duration.ofMinutes(4));

        assertThat(tokens.current()).contains("token-short");
        assertThat(stub.requestsTo(ModoAccessTokens.TOKEN_PATH)).hasSize(1);
    }
}
