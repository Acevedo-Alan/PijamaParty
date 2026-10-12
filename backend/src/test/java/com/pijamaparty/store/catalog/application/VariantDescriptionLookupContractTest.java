package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract of {@link VariantDescriptionLookup} (UC-08). */
public abstract class VariantDescriptionLookupContractTest<T extends VariantDescriptionLookup>
    extends ListedProductsContractTest<T> {

    private static final List<String> M = List.of("M");
    private static final List<String> ROSA = List.of("Rosa");
    private static final List<String> ROSA_GRIS = List.of("Rosa", "Gris");

    private CategoryId summer;

    @BeforeEach
    void createCategory() {
        summer = category("Verano", true).id();
    }

    @Test
    void givenVariantsOfAProduct_whenDescribingThem_thenEachHasItsProductSizeAndColor() {
        Product product = stored(product(summer, "Pijama Luna", 0, List.of("S", "M"), ROSA_GRIS, 5000));
        VariantId small = variant(product, "S", "Gris");
        VariantId medium = variant(product, "M", "Rosa");
        persistAndForget();

        var described = subject.describe(List.of(small, medium));

        assertThat(described).containsOnlyKeys(small, medium);
        assertThat(described.get(small)).isEqualTo(
            new VariantDescription("Pijama Luna", product.slug().value(), "S", "Gris", Optional.empty()));
        assertThat(described.get(medium)).isEqualTo(
            new VariantDescription("Pijama Luna", product.slug().value(), "M", "Rosa", Optional.empty()));
    }

    @Test
    void givenPhotosOfItsColorAndOfTheWholeProduct_whenDescribingAVariant_thenItHasTheFirstPhotoOfItsColor() {
        Product product = stored(product(summer, "Pijama", 0, M, ROSA_GRIS, 5000, p -> p
            .images(1)
            .colorImage("Gris")
            .colorImage("Rosa")
            .colorImage("Rosa")));
        VariantId rosa = variant(product, "M", "Rosa");
        persistAndForget();

        assertThat(subject.describe(List.of(rosa)).get(rosa).photo()).contains(photoOf(product.images().get(2)));
    }

    @Test
    void givenNoPhotoOfItsColor_whenDescribingAVariant_thenItHasTheFirstPhotoOfTheWholeProduct() {
        Product product = stored(product(summer, "Pijama", 0, M, ROSA_GRIS, 5000, p -> p
            .colorImage("Gris")
            .images(2)));
        VariantId rosa = variant(product, "M", "Rosa");
        persistAndForget();

        assertThat(subject.describe(List.of(rosa)).get(rosa).photo()).contains(photoOf(product.images().get(1)));
    }

    @Test
    void givenOnlyPhotosOfOtherColors_whenDescribingAVariant_thenItHasNoPhoto() {
        Product product = stored(product(summer, "Pijama", 0, M, ROSA_GRIS, 5000, p -> p.colorImage("Gris")));
        VariantId rosa = variant(product, "M", "Rosa");
        persistAndForget();

        assertThat(subject.describe(List.of(rosa)).get(rosa).photo()).isEmpty();
    }

    @Test
    void givenVariantsTheShopDoesNotSell_whenDescribingThem_thenTheyAreStillDescribed() {
        Product product = stored(product(summer, "Pijama", 0, List.of("S", "M", "L"), ROSA, 5000, p -> p
            .inactive("M", "Rosa")
            .unpriced("L", "Rosa")));
        Product hidden = stored(product(summer, "Oculto", 1, M, ROSA, 5000, p -> p.hidden()));
        Product ofInactiveCategory = stored(product(category("Invierno", false).id(), "De invierno", 2, M, ROSA, 5000));
        List<VariantId> notSold = List.of(variant(product, "M", "Rosa"), variant(product, "L", "Rosa"),
            variant(hidden, "M", "Rosa"), variant(ofInactiveCategory, "M", "Rosa"));
        persistAndForget();

        assertThat(subject.describe(notSold)).containsOnlyKeys(notSold);
    }

    @Test
    void givenAnUnknownVariant_whenDescribingIt_thenItIsLeftOut() {
        assertThat(subject.describe(List.of(VariantId.of(UUID.randomUUID())))).isEmpty();
    }

    @Test
    void givenNoVariants_whenDescribingThem_thenThereAreNone() {
        assertThat(subject.describe(List.of())).isEmpty();
    }

    private static VariantDescription.Photo photoOf(ProductImage image) {
        return new VariantDescription.Photo(image.placeholder().value(), ProductImage.WIDTHS.stream()
            .map(width -> new VariantDescription.PhotoFile(width, image.storageKey().fileName(width)))
            .toList());
    }
}
