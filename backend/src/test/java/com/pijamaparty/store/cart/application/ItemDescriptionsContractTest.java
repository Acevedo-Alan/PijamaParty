package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ItemDescriptions} (UC-08). */
public abstract class ItemDescriptionsContractTest<T extends ItemDescriptions> extends ContractTest<T> {

    protected static final String PLACEHOLDER = "data:image/webp;base64,UklGRg==";

    /** A variant of a product with this name and slug, which the catalog sells or not, without photos. */
    protected abstract void givenVariant(VariantId variantId, String productName, String slug, String size,
                                         String color, String sku, boolean sold);

    /** Adds a photo of the variant's color to its product, stored under {@code storageKey}. */
    protected abstract void givenPhotoOfItsColor(VariantId variantId, String storageKey);

    @Test
    void givenAVariant_whenDescribingIt_thenItHasItsProductSizeAndColor() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenVariant(variant, "Pijama Luna", "pijama-luna", "M", "Rosa", "PL-M-ROSA", true);

        assertThat(subject.describe(List.of(variant))).isEqualTo(Map.of(variant,
            new ItemDescription("Pijama Luna", "pijama-luna", "M", "Rosa", "PL-M-ROSA", Optional.empty())));
    }

    @Test
    void givenAVariantWithAPhoto_whenDescribingIt_thenItHasThePhotoFilesByWidth() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenVariant(variant, "Pijama Luna", "pijama-luna", "M", "Rosa", "PL-M-ROSA", true);
        givenPhotoOfItsColor(variant, "luna-rosa");

        assertThat(subject.describe(List.of(variant)).get(variant).photo()).contains(new ItemDescription.Photo(
            PLACEHOLDER, List.of(new ItemDescription.PhotoFile(400, "luna-rosa-400.webp"),
                new ItemDescription.PhotoFile(600, "luna-rosa-600.webp"),
                new ItemDescription.PhotoFile(800, "luna-rosa-800.webp"),
                new ItemDescription.PhotoFile(1200, "luna-rosa-1200.webp"))));
    }

    @Test
    void givenAVariantTheCatalogNoLongerSells_whenDescribingIt_thenItIsStillDescribed() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenVariant(variant, "Pijama Sol", "pijama-sol", "S", "Gris", "PS-S-GRIS", false);

        assertThat(subject.describe(List.of(variant))).isEqualTo(Map.of(variant,
            new ItemDescription("Pijama Sol", "pijama-sol", "S", "Gris", "PS-S-GRIS", Optional.empty())));
    }

    @Test
    void givenAnUnknownVariant_whenDescribingIt_thenItIsLeftOut() {
        assertThat(subject.describe(List.of(VariantId.of(UUID.randomUUID())))).isEmpty();
    }

    @Test
    void givenNoVariants_whenDescribingThem_thenNoneComeBack() {
        assertThat(subject.describe(List.of())).isEmpty();
    }
}
