package com.pijamaparty.store.payments.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** RNF-29.3: MercadoPago's Webhooks signature, as its documentation describes it. */
class MercadoPagoSignatureTest {

    private static final String SECRET = "test-webhook-secret";
    private static final Instant NOW = Instant.parse("2026-10-12T15:10:00Z");
    private static final String REQUEST_ID = "bb56a2f1-6aae-46ac-982e-9dcd3581d08e";
    private static final String DATA_ID = "1234567890";

    private final MercadoPagoSignature signature =
        new MercadoPagoSignature(SECRET, Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenTheDocumentedManifest_whenBuilt_thenItListsIdRequestIdAndTimestamp() {
        assertThat(MercadoPagoSignature.manifest("ABC123", REQUEST_ID, "1742505638683"))
            .isEqualTo("id:abc123;request-id:" + REQUEST_ID + ";ts:1742505638683;");
        assertThat(MercadoPagoSignature.manifest(null, null, "1742505638683")).isEqualTo("ts:1742505638683;");
    }

    @Test
    void givenANotificationSignedWithTheSecret_whenVerified_thenItIsValid() {
        String ts = Long.toString(NOW.minusSeconds(5).toEpochMilli());

        assertThat(signature.verify(header(ts, sign(SECRET, DATA_ID, REQUEST_ID, ts)), REQUEST_ID, DATA_ID)).isTrue();
    }

    @Test
    void givenATimestampInSeconds_whenVerified_thenItIsReadAsSeconds() {
        String ts = Long.toString(NOW.minusSeconds(5).getEpochSecond());

        assertThat(signature.verify(header(ts, sign(SECRET, DATA_ID, REQUEST_ID, ts)), REQUEST_ID, DATA_ID)).isTrue();
    }

    @Test
    void givenTheHeaderWithSpacesInAnotherOrderAndAnUppercaseHash_whenVerified_thenItIsValid() {
        String ts = Long.toString(NOW.toEpochMilli());
        String v1 = sign(SECRET, DATA_ID, REQUEST_ID, ts).toUpperCase();

        assertThat(signature.verify("v1=" + v1 + ", ts=" + ts, REQUEST_ID, DATA_ID)).isTrue();
    }

    @Test
    void givenATamperedNotification_whenVerified_thenItIsInvalid() {
        String ts = Long.toString(NOW.toEpochMilli());
        String v1 = sign(SECRET, DATA_ID, REQUEST_ID, ts);

        assertThat(signature.verify(header(ts, v1), REQUEST_ID, "999")).as("another payment").isFalse();
        assertThat(signature.verify(header(ts, v1), "another-request", DATA_ID)).as("another request").isFalse();
        assertThat(signature.verify(header(Long.toString(NOW.toEpochMilli() - 1), v1), REQUEST_ID, DATA_ID))
            .as("another timestamp").isFalse();
        assertThat(signature.verify(header(ts, sign("another-secret", DATA_ID, REQUEST_ID, ts)), REQUEST_ID, DATA_ID))
            .as("another secret").isFalse();
    }

    @Test
    void givenAnOutOfDateNotification_whenVerified_thenItIsInvalid() {
        String old = Long.toString(NOW.minus(Duration.ofHours(24)).minusSeconds(1).toEpochMilli());
        String future = Long.toString(NOW.plus(MercadoPagoSignature.FUTURE_SKEW).plusSeconds(1).toEpochMilli());
        String oldest = Long.toString(NOW.minus(Duration.ofHours(24)).toEpochMilli());

        assertThat(signature.verify(header(old, sign(SECRET, DATA_ID, REQUEST_ID, old)), REQUEST_ID, DATA_ID))
            .isFalse();
        assertThat(signature.verify(header(future, sign(SECRET, DATA_ID, REQUEST_ID, future)), REQUEST_ID, DATA_ID))
            .isFalse();
        assertThat(signature.verify(header(oldest, sign(SECRET, DATA_ID, REQUEST_ID, oldest)), REQUEST_ID, DATA_ID))
            .isTrue();
    }

    @Test
    void givenMalformedHeaders_whenVerified_thenTheyAreInvalidWithoutThrowing() {
        String ts = Long.toString(NOW.toEpochMilli());
        String v1 = sign(SECRET, DATA_ID, REQUEST_ID, ts);

        assertThat(signature.verify(null, REQUEST_ID, DATA_ID)).isFalse();
        assertThat(signature.verify("", REQUEST_ID, DATA_ID)).isFalse();
        assertThat(signature.verify("v1=" + v1, REQUEST_ID, DATA_ID)).as("no ts").isFalse();
        assertThat(signature.verify("ts=" + ts, REQUEST_ID, DATA_ID)).as("no v1").isFalse();
        assertThat(signature.verify("ts=abc,v1=" + v1, REQUEST_ID, DATA_ID)).as("ts not a number").isFalse();
        assertThat(signature.verify("ts=" + ts + ",v1=zz", REQUEST_ID, DATA_ID)).as("v1 not hex").isFalse();
        assertThat(signature.verify("ts=99999999999999999999,v1=" + v1, REQUEST_ID, DATA_ID)).as("huge ts")
            .isFalse();
        assertThat(signature.verify(header(ts, v1) + "x".repeat(600), REQUEST_ID, DATA_ID)).as("too long").isFalse();
    }

    @Test
    void givenNoSecret_whenAnyNotificationIsVerified_thenItIsInvalid() {
        var unconfigured = new MercadoPagoSignature("", Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));
        String ts = Long.toString(NOW.toEpochMilli());

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.verify(header(ts, sign("", DATA_ID, REQUEST_ID, ts)), REQUEST_ID, DATA_ID)).isFalse();
    }

    static String header(String ts, String v1) {
        return "ts=" + ts + ",v1=" + v1;
    }

    /** What MercadoPago computes, written independently of the class under test. */
    static String sign(String secret, String dataId, String requestId, String ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.isEmpty() ? new byte[1] : secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
