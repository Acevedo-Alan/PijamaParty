package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-06, RNF-29.4: the values that describe an uploaded photo. */
class ImageValuesTest {

    @Test
    void givenTheFirstBytes_whenTheFormatIsDetected_thenOnlyJpgPngAndWebpAreRecognized() {
        assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
            .contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}))
            .contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)))
            .contains(ImageFormat.WEBP);

        assertThat(ImageFormat.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect("<svg xmlns=".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect("hello, this is a .jpg".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
    }

    @Test
    void givenDimensions_whenChecked_thenEachSideAndThePixelCountAreBounded() {
        assertThat(new ImageDimensions(10_000, 5_000).withinLimits()).isTrue();
        assertThat(new ImageDimensions(10_001, 10).withinLimits()).isFalse();
        assertThat(new ImageDimensions(10, 10_001).withinLimits()).isFalse();
        assertThat(new ImageDimensions(8_000, 6_250).withinLimits()).isTrue();
        assertThat(new ImageDimensions(8_000, 6_251).withinLimits()).isFalse();
    }

    @Test
    void givenAStorageKey_whenItIsNotServerShaped_thenItIsRejected() {
        UUID id = UUID.fromString("6f1c2a9e-2c39-4b7e-9a35-0e7b2b3f6d10");
        assertThat(StorageKey.of(id).fileName(800)).isEqualTo("6f1c2a9e-2c39-4b7e-9a35-0e7b2b3f6d10-800.webp");

        assertThatThrownBy(() -> new StorageKey("../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageKey("Foto.jpg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageKey("-a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAPlaceholder_whenItIsNotABase64WebpDataUriOfBoundedLength_thenItIsRejected() {
        assertThat(ImagePlaceholder.ofWebp(new byte[] {1, 2, 3}).value()).isEqualTo("data:image/webp;base64,AQID");

        assertThatThrownBy(() -> new ImagePlaceholder("data:image/webp;base64,AAAA\" onerror=\"x"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImagePlaceholder("data:image/svg+xml;base64,AAAA"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImagePlaceholder.ofWebp(new byte[3000])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAFile_whenHashed_thenItIsItsSha256AndSurvivesTheStoredDigest() {
        ContentHash hash = ContentHash.of("abc".getBytes(StandardCharsets.US_ASCII));

        assertThat(hash.hex()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ContentHash.ofDigest(hash.digest())).isEqualTo(hash);
    }
}
