package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link LinePhotos} (UC-14, E7). */
public abstract class LinePhotosContractTest<T extends LinePhotos> extends ContractTest<T> {

    protected static final String PLACEHOLDER = "data:image/webp;base64,UklGRg==";

    /** A variant the catalog has, sold or not, without photos. */
    protected abstract void givenVariant(UUID variantId, boolean sold);

    /** Adds a photo of the variant's color to its product, stored under {@code storageKey}. */
    protected abstract void givenPhotoOfItsColor(UUID variantId, String storageKey);

    @Test
    void givenAVariantWithAPhoto_whenItsPhotoIsAsked_thenItHasThePhotoFilesByWidth() {
        UUID variant = UUID.randomUUID();
        givenVariant(variant, true);
        givenPhotoOfItsColor(variant, "luna-rosa");

        assertThat(subject.photosOf(List.of(variant))).isEqualTo(Map.of(variant, new LinePhotos.Photo(PLACEHOLDER,
            List.of(new LinePhotos.PhotoFile(400, "luna-rosa-400.webp"),
                new LinePhotos.PhotoFile(800, "luna-rosa-800.webp"),
                new LinePhotos.PhotoFile(1200, "luna-rosa-1200.webp")))));
    }

    @Test
    void givenAVariantTheCatalogNoLongerSells_whenItsPhotoIsAsked_thenItStillHasIt() {
        UUID variant = UUID.randomUUID();
        givenVariant(variant, false);
        givenPhotoOfItsColor(variant, "sol-gris");

        assertThat(subject.photosOf(List.of(variant))).containsKey(variant);
    }

    @Test
    void givenAVariantWithoutPhotos_whenItsPhotoIsAsked_thenItIsLeftOut() {
        UUID variant = UUID.randomUUID();
        givenVariant(variant, true);

        assertThat(subject.photosOf(List.of(variant))).isEmpty();
    }

    @Test
    void givenAnUnknownVariant_whenItsPhotoIsAsked_thenItIsLeftOut() {
        assertThat(subject.photosOf(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenNoVariants_whenPhotosAreAsked_thenNoneComeBack() {
        assertThat(subject.photosOf(List.of())).isEmpty();
    }
}
