package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.pijamaparty.store.catalog.domain.ImageDimensions;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link ImageProcessor} (UC-06, RNF-29.4). Scaled heights may round either way, so they are checked
 * within one pixel.
 */
public abstract class ImageProcessorContractTest<T extends ImageProcessor> extends ContractTest<T> {

    private static final String PLACEHOLDER_PREFIX = "data:image/webp;base64,";

    @Test
    void givenA4MegabyteJpg_whenProcessed_thenLighterWebpRenditionsAndAPlaceholderAreMade() {
        byte[] jpeg = TestImages.noisyJpeg(2000, 1500, 1.0f);
        assertThat(jpeg.length).as("the photo of the acceptance criterion").isGreaterThan(4_000_000);

        ImageProcessor.Result.Processed processed = processed(jpeg);

        assertThat(processed.renditions()).extracting(ImageProcessor.Rendition::width)
            .containsExactlyElementsOf(ProductImage.WIDTHS);
        assertThat(processed.renditions()).allSatisfy(rendition ->
            assertThat(rendition.webp().length).isLessThan(jpeg.length));
        assertDimensions(processed, 0, 400, 300);
        assertDimensions(processed, 1, 800, 600);
        assertDimensions(processed, 2, 1200, 900);
        assertPlaceholderFitsTheBox(processed, 10, 8);
    }

    @Test
    void givenATallPhoto_whenProcessed_thenEachRenditionIsBoundedByItsWidthOnly() {
        ImageProcessor.Result.Processed processed = processed(TestImages.png(900, 1600));

        assertDimensions(processed, 0, 400, 711);
        assertDimensions(processed, 1, 800, 1422);
        assertDimensions(processed, 2, 900, 1600);
        assertPlaceholderFitsTheBox(processed, 6, 10);
    }

    @Test
    void givenASmallPng_whenProcessed_thenItIsNeverEnlarged() {
        ImageProcessor.Result.Processed processed = processed(TestImages.png(300, 200));

        for (int rendition = 0; rendition < ProductImage.WIDTHS.size(); rendition++) {
            assertDimensions(processed, rendition, 300, 200);
        }
    }

    @Test
    void givenAWebp_whenProcessed_thenItIsConvertedLikeTheOthers() {
        byte[] webp = processed(TestImages.png(1000, 500)).renditions().get(1).webp();

        ImageProcessor.Result.Processed processed = processed(webp);

        assertDimensions(processed, 0, 400, 200);
        assertDimensions(processed, 2, 800, 400);
    }

    @Test
    void givenAHeaderBeyondTheLimits_whenProcessed_thenItIsRefusedBeforeDecoding() {
        assertThat(subject.process(TestImages.pngHeaderOnly(ImageDimensions.MAX_SIDE + 1, 10)))
            .isEqualTo(new ImageProcessor.Result.TooLarge());
        assertThat(subject.process(TestImages.pngHeaderOnly(8_000, 7_000)))
            .isEqualTo(new ImageProcessor.Result.TooLarge());
    }

    @Test
    void givenGarbageAfterTheJpgMagicBytes_whenProcessed_thenItIsUnreadable() {
        byte[] garbage = new byte[2048];
        new Random(7).nextBytes(garbage);
        garbage[0] = (byte) 0xFF;
        garbage[1] = (byte) 0xD8;
        garbage[2] = (byte) 0xFF;

        assertThat(subject.process(garbage)).isEqualTo(new ImageProcessor.Result.Unreadable());
    }

    protected ImageProcessor.Result.Processed processed(byte[] content) {
        ImageProcessor.Result result = subject.process(content);
        assertThat(result).isInstanceOf(ImageProcessor.Result.Processed.class);
        return (ImageProcessor.Result.Processed) result;
    }

    protected static ImageDimensions dimensions(ImageProcessor.Result.Processed processed, int rendition) {
        return TestImages.webpDimensions(processed.renditions().get(rendition).webp()).orElseThrow();
    }

    private static void assertDimensions(ImageProcessor.Result.Processed processed, int rendition, int width,
                                         int height) {
        ImageDimensions actual = dimensions(processed, rendition);
        assertThat(actual.width()).isEqualTo(width);
        assertThat(actual.height()).isCloseTo(height, within(1));
    }

    private static void assertPlaceholderFitsTheBox(ImageProcessor.Result.Processed processed, int width,
                                                    int height) {
        String value = processed.placeholder().value();
        assertThat(value).startsWith(PLACEHOLDER_PREFIX);
        ImageDimensions actual = TestImages.webpDimensions(
            Base64.getDecoder().decode(value.substring(PLACEHOLDER_PREFIX.length()))).orElseThrow();
        assertThat(actual.width()).isCloseTo(width, within(1)).isLessThanOrEqualTo(10);
        assertThat(actual.height()).isCloseTo(height, within(1)).isLessThanOrEqualTo(10);
    }
}
