package com.pijamaparty.store.payments.adapter.in.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.util.Base64URL;
import com.pijamaparty.store.payments.adapter.out.modo.ModoJwks;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;

/**
 * UC-15 (T-E3b-03), RNF-29.3: the signature of a MODO notification. The body's {@code signature} is a flattened JWS
 * (RFC 7515, section 7.2.2: {@code protected}, {@code payload} and {@code signature}, in base64url) made with one of
 * the keys of MODO's JWK set ({@link ModoJwks}). Only public-key algorithms (RSA and EC) are accepted, so neither
 * {@code none} nor a shared secret can sign. A key the set does not have makes the set be downloaded again, at most
 * every few minutes.
 */
final class ModoSignature {

    /** The parts of a JWS that is a notification; a longer one is not looked at. */
    private static final int MAX_PART_LENGTH = 64 * 1024;

    private final ModoJwks jwks;
    private final DefaultJWSVerifierFactory verifiers = new DefaultJWSVerifierFactory();

    ModoSignature(ModoJwks jwks) {
        this.jwks = jwks;
    }

    /** The signed payload, when {@code signature} is a valid JWS of MODO's; empty otherwise. Never throws. */
    Optional<String> verifiedPayload(JsonNode signature) {
        Optional<JWSObject> jws = parse(signature);
        if (jws.isEmpty() || !isPublicKeyAlgorithm(jws.get().getHeader().getAlgorithm())) {
            return Optional.empty();
        }
        var selector = new JWKSelector(JWKMatcher.forJWSHeader(jws.get().getHeader()));
        List<JWK> candidates = selector.select(jwks.current());
        if (candidates.isEmpty()) {
            candidates = selector.select(jwks.refreshed());
        }
        for (JWK key : candidates) {
            if (verifies(jws.get(), key)) {
                return Optional.of(jws.get().getPayload().toString());
            }
        }
        return Optional.empty();
    }

    private static Optional<JWSObject> parse(JsonNode signature) {
        if (signature == null || !signature.isObject()) {
            return Optional.empty();
        }
        String header = part(signature, "protected");
        String payload = part(signature, "payload");
        String value = part(signature, "signature");
        if (header == null || payload == null || value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JWSObject(new Base64URL(header), new Base64URL(payload), new Base64URL(value)));
        } catch (ParseException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static String part(JsonNode signature, String name) {
        JsonNode part = signature.get(name);
        return part != null && part.isTextual() && !part.asText().isEmpty()
            && part.asText().length() <= MAX_PART_LENGTH ? part.asText() : null;
    }

    private static boolean isPublicKeyAlgorithm(JWSAlgorithm algorithm) {
        return JWSAlgorithm.Family.RSA.contains(algorithm) || JWSAlgorithm.Family.EC.contains(algorithm);
    }

    private boolean verifies(JWSObject jws, JWK key) {
        if (!(key instanceof AsymmetricJWK asymmetric)) {
            return false;
        }
        try {
            JWSVerifier verifier = verifiers.createJWSVerifier(jws.getHeader(), asymmetric.toPublicKey());
            return jws.verify(verifier);
        } catch (JOSEException | IllegalStateException unusable) {
            return false;
        }
    }
}
