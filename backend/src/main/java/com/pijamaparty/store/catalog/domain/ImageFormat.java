package com.pijamaparty.store.catalog.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The photo formats the shop accepts (UC-06). The format comes from the file's first bytes (magic bytes), never from
 * its {@code Content-Type} or name (RNF-29.4).
 */
public enum ImageFormat {

    JPEG,
    PNG,
    WEBP;

    private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_FOURCC = {'W', 'E', 'B', 'P'};

    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_START)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_START)) {
            return Optional.of(PNG);
        }
        // RIFF, a 4-byte size, then WEBP.
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_FOURCC)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] expected) {
        return content.length >= offset + expected.length
            && Arrays.equals(content, offset, offset + expected.length, expected, 0, expected.length);
    }
}
