package com.pijamaparty.store.payments.adapter.in.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * UC-15, RNF-29.3: the signature of a MercadoPago Webhooks notification. The header
 * {@code x-signature: ts={ts},v1={hmac}} signs the manifest {@code id:{data.id};request-id:{x-request-id};ts:{ts};}
 * (a part without a value is left out) with HMAC-SHA256 under the application's secret, in hex. {@code ts} is in
 * milliseconds; a value below 10^11 is read as seconds, as some of MercadoPago's examples write it. Compared in
 * constant time; a notification older than {@code maxAge}, or more than {@link #FUTURE_SKEW} ahead, is refused.
 */
final class MercadoPagoSignature {

    static final Duration FUTURE_SKEW = Duration.ofMinutes(5);

    private static final String ALGORITHM = "HmacSHA256";
    private static final long SECONDS_BELOW = 100_000_000_000L;
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,15}");
    private static final Pattern HEX_SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MAX_HEADER_LENGTH = 500;

    private final byte[] secret;
    private final Duration maxAge;
    private final Clock clock;

    MercadoPagoSignature(String secret, Duration maxAge, Clock clock) {
        this.secret = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    boolean isConfigured() {
        return secret.length > 0;
    }

    /** Whether the notification of {@code dataId} carries a valid, current signature. Never throws for its input. */
    boolean verify(String signatureHeader, String requestId, String dataId) {
        if (!isConfigured() || signatureHeader == null || tooLong(signatureHeader) || tooLong(requestId)
            || tooLong(dataId)) {
            return false;
        }
        Optional<Parts> parts = Parts.parse(signatureHeader);
        if (parts.isEmpty() || !isCurrent(parts.get().timestamp())) {
            return false;
        }
        byte[] expected = hmac(manifest(dataId, requestId, parts.get().timestamp()));
        byte[] received = HexFormat.of().parseHex(parts.get().v1().toLowerCase(Locale.ROOT));
        return MessageDigest.isEqual(expected, received);
    }

    /** The text MercadoPago signs; the id goes in lower case, as its documentation asks for alphanumeric ids. */
    static String manifest(String dataId, String requestId, String timestamp) {
        var manifest = new StringBuilder();
        if (dataId != null && !dataId.isEmpty()) {
            manifest.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (requestId != null && !requestId.isEmpty()) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        manifest.append("ts:").append(timestamp).append(';');
        return manifest.toString();
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_HEADER_LENGTH;
    }

    private boolean isCurrent(String timestamp) {
        long value = Long.parseLong(timestamp);
        Instant signedAt = value < SECONDS_BELOW ? Instant.ofEpochSecond(value) : Instant.ofEpochMilli(value);
        Instant now = clock.instant();
        return !signedAt.isBefore(now.minus(maxAge)) && !signedAt.isAfter(now.plus(FUTURE_SKEW));
    }

    private byte[] hmac(String manifest) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** {@code ts=...,v1=...} in any order, with optional spaces; other parts are ignored. */
    private record Parts(String timestamp, String v1) {

        static Optional<Parts> parse(String header) {
            String timestamp = null;
            String v1 = null;
            for (String part : header.split(",")) {
                int equals = part.indexOf('=');
                if (equals < 0) {
                    continue;
                }
                String key = part.substring(0, equals).trim();
                String value = part.substring(equals + 1).trim();
                if (key.equals("ts")) {
                    timestamp = value;
                } else if (key.equals("v1")) {
                    v1 = value;
                }
            }
            if (timestamp == null || v1 == null || !TIMESTAMP.matcher(timestamp).matches()
                || !HEX_SHA256.matcher(v1).matches()) {
                return Optional.empty();
            }
            return Optional.of(new Parts(timestamp, v1));
        }
    }
}
