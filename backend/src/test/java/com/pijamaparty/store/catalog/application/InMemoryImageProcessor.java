package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ImageDimensions;
import com.pijamaparty.store.catalog.domain.ImageFormat;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.ProductImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Fake of {@link ImageProcessor} without libvips: it reads the dimensions from the header (ImageIO for JPG and PNG, the
 * VP8X/VP8/VP8L header for WebP) and writes header-only WebPs of the dimensions libvips would produce. It does not
 * rotate by EXIF, which only the real adapter's own test checks.
 */
public class InMemoryImageProcessor implements ImageProcessor {

    private static final int PLACEHOLDER_BOX = 10;

    private final AtomicInteger conversions = new AtomicInteger();

    /** How many photos it converted, so tests can see that a refused upload was never converted. */
    public int conversions() {
        return conversions.get();
    }

    @Override
    public Result process(byte[] content) {
        Optional<ImageDimensions> found = dimensionsOf(content);
        if (found.isEmpty()) {
            return new Result.Unreadable();
        }
        ImageDimensions source = found.get();
        if (!source.withinLimits()) {
            return new Result.TooLarge();
        }
        conversions.incrementAndGet();
        List<Rendition> renditions = ProductImage.WIDTHS.stream()
            .map(width -> new Rendition(width, webp(source, Math.min(1.0, (double) width / source.width()))))
            .toList();
        double placeholderScale = Math.min(1.0,
            (double) PLACEHOLDER_BOX / Math.max(source.width(), source.height()));
        return new Result.Processed(renditions, ImagePlaceholder.ofWebp(webp(source, placeholderScale)));
    }

    private static byte[] webp(ImageDimensions source, double scale) {
        return TestImages.webpHeaderOnly(Math.max(1, (int) Math.round(source.width() * scale)),
            Math.max(1, (int) Math.round(source.height() * scale)));
    }

    private static Optional<ImageDimensions> dimensionsOf(byte[] content) {
        if (ImageFormat.detect(content).orElse(null) == ImageFormat.WEBP) {
            return TestImages.webpDimensions(content);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return Optional.of(new ImageDimensions(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
