package com.pijamaparty.store.catalog.adapter.out.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pijamaparty.store.catalog.application.ImageProcessor;
import com.pijamaparty.store.catalog.application.ImageProcessorContractTest;
import com.pijamaparty.store.catalog.application.TestImages;
import com.pijamaparty.store.catalog.domain.ImageDimensions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The libvips adapter against the real command line. Skipped where libvips is not installed (see VIPS_BIN_DIR in
 * .env.example), except in CI, which installs it and must never skip it.
 */
class VipsImageProcessorContractTest extends ImageProcessorContractTest<VipsImageProcessor> {

    private static final String BIN_DIR = System.getenv().getOrDefault("VIPS_BIN_DIR", "");

    @BeforeAll
    static void requireLibvips() {
        if (System.getenv("CI") == null) {
            assumeTrue(libvipsRuns(), "libvips is not installed: the libvips adapter is not tested here");
        }
    }

    @Override
    protected VipsImageProcessor createSubject() {
        return new VipsImageProcessor(BIN_DIR, Duration.ofSeconds(30));
    }

    @Test
    void givenAJpgRotatedByExif_whenProcessed_thenItIsTurnedAndItsMetadataIsGone() {
        byte[] rotated = TestImages.withExifOrientation(TestImages.noisyJpeg(600, 400, 0.9f), 6);

        ImageProcessor.Result.Processed processed = processed(rotated);

        assertThat(dimensions(processed, 0)).isEqualTo(new ImageDimensions(400, 600));
        assertThat(processed.renditions()).allSatisfy(rendition -> {
            String text = new String(rendition.webp(), StandardCharsets.ISO_8859_1);
            assertThat(text).doesNotContain("EXIF", "Exif", "ICCP", "XMP ");
        });
    }

    private static boolean libvipsRuns() {
        String vips = BIN_DIR.isBlank() ? "vips" : java.nio.file.Path.of(BIN_DIR, "vips").toString();
        try {
            Process process = new ProcessBuilder(vips, "--version").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
