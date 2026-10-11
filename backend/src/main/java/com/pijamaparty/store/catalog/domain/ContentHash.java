package com.pijamaparty.store.catalog.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** SHA-256 of an uploaded file, in lower-case hex: tells a retried upload from a reused key (UC-06). */
public record ContentHash(String hex) {

    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{64}");

    public ContentHash {
        Objects.requireNonNull(hex, "hex");
        if (!FORMAT.matcher(hex).matches()) {
            throw new IllegalArgumentException("not a SHA-256 in hex");
        }
    }

    public static ContentHash of(byte[] content) {
        try {
            return ofDigest(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform has SHA-256", e);
        }
    }

    /** A stored digest, as {@link #digest()} returned it. */
    public static ContentHash ofDigest(byte[] digest) {
        return new ContentHash(HexFormat.of().formatHex(digest));
    }

    public byte[] digest() {
        return HexFormat.of().parseHex(hex);
    }
}
