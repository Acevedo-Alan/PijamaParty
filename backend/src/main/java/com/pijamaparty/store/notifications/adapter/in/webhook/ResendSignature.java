package com.pijamaparty.store.notifications.adapter.in.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * UC-17 (T-E3-22), RNF-29.3: the signature of a Resend webhook notification, which Resend signs through Svix. The
 * header {@code svix-signature} holds space-separated {@code v1,{base64}} entries, each an HMAC-SHA256 of
 * {@code {svix-id}.{svix-timestamp}.{body}} under the key the secret ({@code whsec_} and the key in base64) carries.
 * Any entry that matches, compared in constant time, is enough. {@code svix-timestamp} is in seconds; one more than
 * {@link #TOLERANCE} off the clock, either way, is refused (each delivery attempt is signed again).
 */
final class ResendSignature {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SECRET_PREFIX = "whsec_";
    private static final String VERSION = "v1,";
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,15}");
    private static final int MAX_ID_LENGTH = 200;
    private static final int MAX_SIGNATURE_LENGTH = 1000;

    private final byte[] key;
    private final Clock clock;

    /** @throws IllegalArgumentException when a secret is given but its key is not base64 */
    ResendSignature(String secret, Clock clock) {
        Objects.requireNonNull(secret, "secret");
        String encodedKey = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        try {
            this.key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("the Resend webhook secret is not whsec_ and a base64 key", e);
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    boolean isConfigured() {
        return key.length > 0;
    }

    /** Whether the notification carries a valid, current signature of its exact body. Never throws for its input. */
    boolean verify(String id, String timestamp, String signatureHeader, byte[] body) {
        if (!isConfigured() || id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH || timestamp == null
            || !TIMESTAMP.matcher(timestamp).matches() || signatureHeader == null
            || signatureHeader.length() > MAX_SIGNATURE_LENGTH || body == null || !isCurrent(timestamp)) {
            return false;
        }
        byte[] expected = hmac(id, timestamp, body);
        boolean matches = false;
        for (String entry : signatureHeader.split(" ")) {
            if (entry.startsWith(VERSION)) {
                // Every entry is compared, so the time taken does not tell which one matched.
                matches |= MessageDigest.isEqual(expected, decode(entry.substring(VERSION.length())));
            }
        }
        return matches;
    }

    private boolean isCurrent(String timestamp) {
        Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
        Instant now = clock.instant();
        return !signedAt.isBefore(now.minus(TOLERANCE)) && !signedAt.isAfter(now.plus(TOLERANCE));
    }

    private byte[] hmac(String id, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static byte[] decode(String signature) {
        try {
            return Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
