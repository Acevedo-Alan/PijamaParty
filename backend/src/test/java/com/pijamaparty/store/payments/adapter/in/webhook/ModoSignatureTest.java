package com.pijamaparty.store.payments.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.pijamaparty.store.payments.adapter.out.modo.ModoJwks;
import com.pijamaparty.store.payments.adapter.out.modo.ModoStub;
import com.pijamaparty.store.platform.testing.MovableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * UC-15 (T-E3b-03), RNF-29.3: a MODO notification is accepted only with a JWS of MODO's published keys, and the JWK
 * set is downloaded once and again only for an unknown key, at most every 5 minutes.
 */
class ModoSignatureTest {

    private static final String PAYLOAD = "{\"id\":\"pr-1\",\"status\":\"ACCEPTED\"}";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ModoStub modo = new ModoStub();
    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-15T15:00:00Z"));
    private final ModoSignature signature = new ModoSignature(new ModoJwks(modo.baseUrl(), Duration.ofSeconds(1),
        Duration.ofSeconds(2), "PijamaParty", clock));

    @AfterEach
    void stopStub() {
        modo.stop();
    }

    @Test
    void givenANotificationSignedByMODO_whenVerified_thenItsPayloadIsTheSignedOne() {
        assertThat(signature.verifiedPayload(modo.signature(PAYLOAD))).contains(PAYLOAD);
    }

    @Test
    void givenSeveralNotifications_whenVerified_thenTheKeysAreDownloadedOnce() {
        signature.verifiedPayload(modo.signature(PAYLOAD));
        signature.verifiedPayload(modo.signature(PAYLOAD));

        assertThat(modo.requestsTo(ModoStub.JWKS_PATH)).hasSize(1);
        assertThat(modo.requestsTo(ModoStub.JWKS_PATH).getFirst().userAgent()).isEqualTo("PijamaParty");
    }

    @Test
    void givenATamperedPayload_whenVerified_thenItIsRefused() {
        ObjectNode signed = modo.signature(PAYLOAD);
        signed.put("payload", base64Url("{\"id\":\"pr-1\",\"status\":\"ACCEPTED\",\"amount\":1}"));

        assertThat(signature.verifiedPayload(signed)).isEmpty();
    }

    @Test
    void givenANotificationSignedWithAnotherKeyUnderMODOsKeyId_whenVerified_thenItIsRefused() {
        var forged = ModoStub.signature(PAYLOAD, new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("modo-key-0").build(),
            ModoStub.signer(ModoStub.newKey("modo-key-0")));

        assertThat(signature.verifiedPayload(forged)).isEmpty();
    }

    @Test
    void givenASharedSecretSignature_whenVerified_thenItIsRefusedWhateverTheKey() throws JOSEException {
        var hmac = ModoStub.signature(PAYLOAD, new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("modo-key-0").build(),
            new MACSigner("a-secret-of-at-least-thirty-two-bytes!".getBytes(StandardCharsets.UTF_8)));

        assertThat(signature.verifiedPayload(hmac)).isEmpty();
    }

    @Test
    void givenAnUnsignedJws_whenVerified_thenItIsRefused() {
        ObjectNode none = JSON.createObjectNode()
            .put("protected", base64Url("{\"alg\":\"none\"}"))
            .put("payload", base64Url(PAYLOAD))
            .put("signature", "");

        assertThat(signature.verifiedPayload(none)).isEmpty();
    }

    @Test
    void givenMissingOrMalformedSignatures_whenVerified_thenTheyAreRefusedWithoutThrowing() {
        ObjectNode withoutPayload = modo.signature(PAYLOAD);
        withoutPayload.remove("payload");
        ObjectNode notBase64 = modo.signature(PAYLOAD);
        notBase64.put("protected", "{not base64}");

        assertThat(signature.verifiedPayload(null)).isEmpty();
        assertThat(signature.verifiedPayload(JSON.getNodeFactory().textNode("a.b.c"))).isEmpty();
        assertThat(signature.verifiedPayload(withoutPayload)).isEmpty();
        assertThat(signature.verifiedPayload(notBase64)).isEmpty();
    }

    @Test
    void givenMODORotatedItsKey_whenANotificationIsSignedWithTheNewOne_thenTheKeysAreDownloadedAgain() {
        signature.verifiedPayload(modo.signature(PAYLOAD));
        modo.rotateKey();
        clock.advance(ModoJwks.MIN_INTERVAL);

        assertThat(signature.verifiedPayload(modo.signature(PAYLOAD))).contains(PAYLOAD);
        assertThat(modo.requestsTo(ModoStub.JWKS_PATH)).hasSize(2);
    }

    @Test
    void givenUnknownKeysOverAndOver_whenVerified_thenTheKeysAreDownloadedAtMostEveryFiveMinutes() {
        signature.verifiedPayload(modo.signature(PAYLOAD));
        var unknown = ModoStub.signature(PAYLOAD, new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("forged").build(),
            ModoStub.signer(ModoStub.newKey("forged")));

        for (int i = 0; i < 5; i++) {
            assertThat(signature.verifiedPayload(unknown)).isEmpty();
        }
        assertThat(modo.requestsTo(ModoStub.JWKS_PATH)).hasSize(1);

        clock.advance(Duration.ofMinutes(5));
        signature.verifiedPayload(unknown);
        assertThat(modo.requestsTo(ModoStub.JWKS_PATH)).hasSize(2);
    }

    @Test
    void givenTheKeysCannotBeDownloaded_whenVerified_thenTheNotificationIsRefusedWithoutThrowing() {
        modo.stop();

        assertThat(signature.verifiedPayload(modo.signature(PAYLOAD))).isEmpty();
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
