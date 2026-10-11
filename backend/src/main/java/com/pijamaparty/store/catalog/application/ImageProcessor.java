package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ImageDimensions;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.ProductImage;
import java.util.List;

/**
 * Outbound port: converts an uploaded photo to WebP (UC-06, RNF-29.4). It reads the dimensions from the header and
 * refuses a photo outside {@link ImageDimensions#withinLimits()} before decoding it. It applies the orientation, removes
 * every metadata block, converts colors to sRGB and keeps the first frame of an animation.
 */
public interface ImageProcessor {

    /** {@code content} is a JPG, PNG or WebP by its first bytes ({@code ImageFormat}). */
    Result process(byte[] content);

    sealed interface Result {

        /**
         * One rendition per width of {@link ProductImage#WIDTHS}, in that order, at most that wide and never wider
         * than the photo; the placeholder fits in 10 × 10 pixels.
         */
        record Processed(List<Rendition> renditions, ImagePlaceholder placeholder) implements Result {

            public Processed {
                renditions = List.copyOf(renditions);
            }
        }

        /** The header states dimensions beyond the limits. */
        record TooLarge() implements Result {}

        /** Not a valid image of its format. */
        record Unreadable() implements Result {}
    }

    /** A WebP file for the rendition of {@code width} (the target width, not the actual one). */
    record Rendition(int width, byte[] webp) {}
}
