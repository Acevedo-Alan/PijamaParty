package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.PLACEHOLDER;
import static com.pijamaparty.store.catalog.application.ListingFixture.T0;
import static com.pijamaparty.store.catalog.application.ListingFixture.category;
import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-03: a visitor opens a product. */
class GetProductDetailServiceTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final GetProductDetail detail = new GetProductDetailService(categories, products, availability);

    private final Category summer = saved(category("Verano", true));

    @Test
    void givenPinkMInStockAndLightBlueMOutOfStock_whenTheDetailOpens_thenOnlyLightBlueMHasNothingAvailable() {
        Product pajama = saved(product(summer.id(), "Pijama", 0, List.of("M"), List.of("Rosa", "Celeste"), 4500));
        availability.setAvailable(variant(pajama, "M", "Rosa"), 3);

        ProductDetail found = found("pijama");

        assertThat(found.variants()).containsExactly(
            new ProductDetail.VariantDetail(variant(pajama, "M", "Rosa"), sku(pajama, "M", "Rosa"), "M", "Rosa",
                Money.of(4500), 3),
            new ProductDetail.VariantDetail(variant(pajama, "M", "Celeste"), sku(pajama, "M", "Celeste"), "M",
                "Celeste", Money.of(4500), 0));
    }

    @Test
    void givenAProduct_whenTheDetailOpens_thenItShowsItsDetailsAndEachVariantHasItsOwnPrice() {
        saved(product(summer.id(), "Pijama de seda", 0, List.of("S", "M"), List.of("Rosa"), 5000,
            p -> p.material("Seda").price("M", "Rosa", 5600)));

        ProductDetail found = found("pijama-de-seda");

        assertThat(found.name()).isEqualTo("Pijama de seda");
        assertThat(found.material()).isEqualTo("Seda");
        assertThat(found.fromPrice()).isEqualTo(Money.of(5000));
        assertThat(found.sizes()).containsExactly("S", "M");
        assertThat(found.colors()).containsExactly("Rosa");
        assertThat(found.variants()).extracting(ProductDetail.VariantDetail::price)
            .containsExactly(Money.of(5000), Money.of(5600));
    }

    @Test
    void givenInactiveAndUnpricedVariants_whenTheDetailOpens_thenOnlyTheSellableOnesAndTheirColorsPhotosAreShown() {
        Product pajama = product(summer.id(), "Pijama", 0, List.of("S", "M", "L"), List.of("Rosa", "Celeste"), 4500,
            p -> p.inactive("L", "Rosa").inactive("L", "Celeste").inactive("S", "Celeste").unpriced("M", "Celeste")
                .images(1));
        addImage(pajama, "Celeste");
        addImage(pajama, "Rosa");
        saved(pajama);

        ProductDetail found = found("pijama");

        assertThat(found.sizes()).containsExactly("S", "M");
        assertThat(found.colors()).containsExactly("Rosa");
        assertThat(found.variants()).extracting(ProductDetail.VariantDetail::size, ProductDetail.VariantDetail::color)
            .containsExactly(tuple("S", "Rosa"), tuple("M", "Rosa"));
        assertThat(found.images()).extracting(ProductView.ImageView::color)
            .containsExactly(Optional.empty(), Optional.of("Rosa"));
    }

    @Test
    void givenProductsVisitorsDoNotSee_whenOpened_thenTheyAreNotFound() {
        saved(product(summer.id(), "Oculto", 0, List.of("M"), List.of("Rosa"), 1000, p -> p.hidden()));
        Category closed = saved(category("Cerrada", false));
        saved(product(closed.id(), "De cerrada", 1, List.of("M"), List.of("Rosa"), 1000));

        assertThat(detail.execute(new GetProductDetail.Query("oculto")))
            .isEqualTo(new GetProductDetail.Result.NotFound());
        assertThat(detail.execute(new GetProductDetail.Query("de-cerrada")))
            .isEqualTo(new GetProductDetail.Result.NotFound());
    }

    @Test
    void givenASlugThatDoesNotExistOrIsNotOne_whenOpened_thenItIsNotFound() {
        assertThat(detail.execute(new GetProductDetail.Query("no-existe")))
            .isEqualTo(new GetProductDetail.Result.NotFound());
        assertThat(detail.execute(new GetProductDetail.Query("No Existe!")))
            .isEqualTo(new GetProductDetail.Result.NotFound());
    }

    private ProductDetail found(String slug) {
        return ((GetProductDetail.Result.Found) detail.execute(new GetProductDetail.Query(slug))).product();
    }

    private static String sku(Product product, String size, String color) {
        VariantId id = variant(product, size, color);
        return product.variants().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow().sku().value();
    }

    private static void addImage(Product product, String color) {
        product.addImage(new Product.NewImage(ImageId.of(UUID.randomUUID()), Optional.of(new Color(color)),
            StorageKey.of(UUID.randomUUID()), new ImagePlaceholder(PLACEHOLDER), UploadKey.of(UUID.randomUUID()),
            ContentHash.of(color.getBytes(StandardCharsets.UTF_8))), T0);
    }

    private Category saved(Category category) {
        categories.save(category);
        return category;
    }

    private Product saved(Product product) {
        products.save(product);
        return product;
    }
}
