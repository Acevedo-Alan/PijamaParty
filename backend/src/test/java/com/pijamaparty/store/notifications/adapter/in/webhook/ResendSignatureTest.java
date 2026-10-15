package com.pijamaparty.store.notifications.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** RNF-29.3 (T-E3-22): the Svix signature of Resend's webhook notifications, as Svix's documentation describes it. */
class ResendSignatureTest {

    // Svix's documented example: this secret, id, timestamp and body give this signature.
    private static final String SVIX_SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final String SVIX_ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final String SVIX_TIMESTAMP = "1614265330";
    private static final byte[] SVIX_BODY = "{\"test\": 2432232314}".getBytes(StandardCharsets.UTF_8);
    private static final String SVIX_SIGNATURE = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";

    private static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");
    private static final String TS = Long.toString(NOW.getEpochSecond());
    private static final byte[] BODY = "{\"type\":\"email.bounced\"}".getBytes(StandardCharsets.UTF_8);

    private final ResendSignature signature =
        new ResendSignature(ResendWebhooks.SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenSvixsDocumentedExample_whenVerifiedAtItsTime_thenItIsValid() {
        var atTheExample = new ResendSignature(SVIX_SECRET,
            Clock.fixed(Instant.ofEpochSecond(Long.parseLong(SVIX_TIMESTAMP)), ZoneOffset.UTC));

        assertThat(atTheExample.verify(SVIX_ID, SVIX_TIMESTAMP, SVIX_SIGNATURE, SVIX_BODY)).isTrue();
        assertThat(ResendWebhooks.sign(SVIX_SECRET, SVIX_ID, SVIX_TIMESTAMP, SVIX_BODY)).isEqualTo(SVIX_SIGNATURE);
    }

    @Test
    void givenANotificationSignedWithTheSecret_whenVerified_thenItIsValid() {
        assertThat(signature.verify("msg_1", TS, ResendWebhooks.sign("msg_1", TS, BODY), BODY)).isTrue();
    }

    @Test
    void givenSeveralSignaturesOneOfThemValid_whenVerified_thenItIsValid() {
        String header = "v1,bm90IHRoaXMgb25l v2,whatever " + ResendWebhooks.sign("msg_1", TS, BODY);

        assertThat(signature.verify("msg_1", TS, header, BODY)).isTrue();
    }

    @Test
    void givenATamperedNotification_whenVerified_thenItIsInvalid() {
        String valid = ResendWebhooks.sign("msg_1", TS, BODY);

        assertThat(signature.verify("msg_1", TS, valid, "{\"type\":\"email.complained\"}".getBytes(
            StandardCharsets.UTF_8))).as("another body").isFalse();
        assertThat(signature.verify("msg_2", TS, valid, BODY)).as("another id").isFalse();
        assertThat(signature.verify("msg_1", Long.toString(NOW.getEpochSecond() - 1), valid, BODY))
            .as("another timestamp").isFalse();
        assertThat(signature.verify("msg_1", TS, ResendWebhooks.sign("whsec_b3RoZXI=", "msg_1", TS, BODY), BODY))
            .as("another secret").isFalse();
    }

    @Test
    void givenATimestampMoreThanFiveMinutesOff_whenVerified_thenItIsOutOfDate() {
        String late = Long.toString(NOW.minus(ResendSignature.TOLERANCE).minusSeconds(1).getEpochSecond());
        String early = Long.toString(NOW.plus(ResendSignature.TOLERANCE).plusSeconds(1).getEpochSecond());
        String edge = Long.toString(NOW.minus(ResendSignature.TOLERANCE).getEpochSecond());

        assertThat(signature.verify("msg_1", late, ResendWebhooks.sign("msg_1", late, BODY), BODY)).isFalse();
        assertThat(signature.verify("msg_1", early, ResendWebhooks.sign("msg_1", early, BODY), BODY)).isFalse();
        assertThat(signature.verify("msg_1", edge, ResendWebhooks.sign("msg_1", edge, BODY), BODY)).isTrue();
    }

    @Test
    void givenMissingOrMalformedHeaders_whenVerified_thenItIsInvalidWithoutThrowing() {
        String valid = ResendWebhooks.sign("msg_1", TS, BODY);

        assertThat(signature.verify(null, TS, valid, BODY)).isFalse();
        assertThat(signature.verify("", TS, valid, BODY)).isFalse();
        assertThat(signature.verify("m".repeat(201), TS, valid, BODY)).isFalse();
        assertThat(signature.verify("msg_1", null, valid, BODY)).isFalse();
        assertThat(signature.verify("msg_1", "1.5", valid, BODY)).isFalse();
        assertThat(signature.verify("msg_1", "9".repeat(16), valid, BODY)).isFalse();
        assertThat(signature.verify("msg_1", TS, null, BODY)).isFalse();
        assertThat(signature.verify("msg_1", TS, "v1,%%%not-base64", BODY)).isFalse();
        assertThat(signature.verify("msg_1", TS, valid.substring(3), BODY)).as("without its version").isFalse();
        assertThat(signature.verify("msg_1", TS, valid + " ".repeat(1000), BODY)).as("too long").isFalse();
    }

    @Test
    void givenNoSecret_whenVerified_thenEveryNotificationIsRefused() {
        var unconfigured = new ResendSignature("", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.verify("msg_1", TS, ResendWebhooks.sign("whsec_", "msg_1", TS, BODY), BODY))
            .isFalse();
    }

    @Test
    void givenASecretWhoseKeyIsNotBase64_whenCreated_thenItIsRefused() {
        assertThatThrownBy(() -> new ResendSignature("whsec_%%%", Clock.systemUTC()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("%%%");
    }
}
