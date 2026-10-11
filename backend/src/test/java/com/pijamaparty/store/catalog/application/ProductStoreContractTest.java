package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.Description;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductDetails;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.ProductName;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.catalog.domain.Sku;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.catalog.domain.VariantIdentities;
import com.pijamaparty.store.catalog.domain.VariantMatrix;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadProducts}, {@link SaveProduct} and {@link ProductSummaries}. */
public abstract class ProductStoreContractTest<T extends LoadProducts & SaveProduct & ProductSummaries>
    extends ContractTest<T> {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T12:00:00Z");

    private CategoryId category;

    /** Creates a category that products can belong to (the product's foreign key). */
    protected abstract CategoryId givenACategory();

    @BeforeEach
    void createCategory() {
        category = givenACategory();
    }

    @Test
    void givenASavedProductWithVariants_whenLoadingIt_thenItHasTheSameState() {
        Product product = product("Pijama de Algodón", CREATED);
        assertThat(subject.add(product)).isEqualTo(SaveProduct.Outcome.SAVED);
        generate(product, List.of("S", "M"), List.of("Rosa", "Celeste"), "PP-A");
        assertThat(subject.update(product)).isEqualTo(SaveProduct.Outcome.SAVED);
        persistAndForget();

        assertSameState(subject.findById(product.id()).orElseThrow(), product);
        assertSameState(subject.findBySlug(new Slug("pijama-de-algodon")).orElseThrow(), product);
        assertSameState(subject.lockById(product.id()).orElseThrow(), product);
        assertSameState(subject.lockByVariantId(product.variants().get(3).id()).orElseThrow(), product);
    }

    @Test
    void givenASavedProduct_whenItAndItsVariantsChangeAndItIsSavedAgain_thenTheChangesAreKept() {
        Product product = product("Pijama", CREATED);
        subject.add(product);
        generate(product, List.of("S", "M"), List.of("Rosa"), "PP-A");
        subject.update(product);
        persistAndForget();

        Product loaded = subject.lockById(product.id()).orElseThrow();
        loaded.update(new ProductDetails(category, new ProductName("Pijama de Seda"), new Description("Suave.\nFresco."),
            new Material("Seda"), Season.WINTER, Audience.KIDS), LATER);
        VariantId first = loaded.variants().getFirst().id();
        loaded.changeVariant(first, Optional.of(Money.of(2_500_000)), Optional.empty(), LATER);
        loaded.changeVariant(loaded.variants().get(1).id(), Optional.empty(), Optional.of(false), LATER);
        generate(loaded, List.of("XS"), List.of("Rosa"), "PP-B");
        loaded.publish(LATER);
        assertThat(subject.update(loaded)).isEqualTo(SaveProduct.Outcome.SAVED);
        persistAndForget();

        Product stored = subject.findById(product.id()).orElseThrow();
        assertSameState(stored, loaded);
        assertThat(stored.sizes()).extracting(size -> size.value()).containsExactly("XS", "S", "M");
        assertThat(stored.variants().get(1).price()).contains(Money.of(2_500_000));
    }

    @Test
    void givenPhotos_whenAddedReorderedAndRemoved_thenTheStoredProductHasTheSamePhotos() {
        Product product = product("Pijama", CREATED);
        subject.add(product);
        generate(product, List.of("M"), List.of("Rosa"), "PP-A");
        ImageId plain = addImage(product, Optional.empty(), 1);
        ImageId pink = addImage(product, Optional.of(new Color("Rosa")), 2);
        ImageId last = addImage(product, Optional.empty(), 3);
        assertThat(subject.update(product)).isEqualTo(SaveProduct.Outcome.SAVED);
        persistAndForget();
        assertSameState(subject.findById(product.id()).orElseThrow(), product);

        Product loaded = subject.lockById(product.id()).orElseThrow();
        loaded.reorderImages(List.of(last, pink, plain));
        loaded.removeImage(pink);
        assertThat(subject.update(loaded)).isEqualTo(SaveProduct.Outcome.SAVED);
        persistAndForget();

        Product stored = subject.findById(product.id()).orElseThrow();
        assertSameState(stored, loaded);
        assertThat(stored.images()).extracting(ProductImage::id).containsExactly(last, plain);

        loaded.removeImage(last);
        loaded.removeImage(plain);
        subject.update(loaded);
        persistAndForget();
        assertThat(subject.findById(product.id()).orElseThrow().images()).isEmpty();
    }

    @Test
    void givenASlugInUse_whenAnotherProductWithTheSameSlugIsSaved_thenItIsNotSavedAndTheStoreStaysUsable() {
        Product existing = product("Pijama corto", CREATED);
        subject.add(existing);

        Product clash = product("PIJAMA CORTO", CREATED);
        assertThat(subject.add(clash)).isEqualTo(SaveProduct.Outcome.SLUG_ALREADY_USED);
        Product renamed = product("Camisón", CREATED);
        subject.add(renamed);
        persistAndForget();
        Product loaded = subject.findById(renamed.id()).orElseThrow();
        loaded.update(details("Pijama corto"), LATER);
        assertThat(subject.update(loaded)).isEqualTo(SaveProduct.Outcome.SLUG_ALREADY_USED);

        Product other = product("Bata", CREATED);
        assertThat(subject.add(other)).isEqualTo(SaveProduct.Outcome.SAVED);
        persistAndForget();
        assertThat(subject.findById(clash.id())).isEmpty();
        assertThat(subject.findById(renamed.id()).orElseThrow().slug().value()).isEqualTo("camison");
        assertThat(subject.findById(other.id())).isPresent();
    }

    @Test
    void givenASkuInUse_whenANewVariantGetsIt_thenNothingOfThatWriteIsSaved() {
        Product existing = product("Pijama", CREATED);
        subject.add(existing);
        generate(existing, List.of("M"), List.of("Rosa"), "PP-A");
        subject.update(existing);
        Product other = product("Camisón", CREATED);
        subject.add(other);
        persistAndForget();

        Product loaded = subject.lockById(other.id()).orElseThrow();
        loaded.update(details("Camisón largo"), LATER);
        generate(loaded, List.of("M", "L"), List.of("Rosa"), "PP-B", existing.variants().getFirst().sku().value());

        assertThat(subject.update(loaded)).isEqualTo(SaveProduct.Outcome.SKU_ALREADY_USED);
        persistAndForget();
        Product stored = subject.findById(other.id()).orElseThrow();
        assertThat(stored.variants()).isEmpty();
        assertThat(stored.details().name().value()).isEqualTo("Camisón");
    }

    @Test
    void givenAnUnknownCategory_whenAProductIsSaved_thenItIsNotSaved() {
        Product product = Product.create(ProductId.of(UUID.randomUUID()), new ProductDetails(
            CategoryId.of(UUID.randomUUID()), new ProductName("Pijama"), new Description(""), new Material("Algodón"),
            Season.SUMMER, Audience.WOMEN), CREATED);

        assertThat(subject.add(product)).isEqualTo(SaveProduct.Outcome.UNKNOWN_CATEGORY);
        persistAndForget();
        assertThat(subject.findById(product.id())).isEmpty();
    }

    @Test
    void givenSeveralProducts_whenListingThem_thenTheyComeNewestFirstPageByPageWithTheirVariantCount() {
        Product oldest = product("Pijama", CREATED);
        Product middle = product("Camisón", CREATED.plusSeconds(60));
        Product newest = product("Bata", CREATED.plusSeconds(120));
        subject.add(middle);
        subject.add(newest);
        subject.add(oldest);
        generate(oldest, List.of("S", "M"), List.of("Rosa"), "PP-A");
        subject.update(oldest);
        persistAndForget();

        assertThat(subject.list(0, 2)).containsExactly(
            new ProductSummaryView(newest.id(), category, "Bata", "bata", false, 0),
            new ProductSummaryView(middle.id(), category, "Camisón", "camison", false, 0));
        assertThat(subject.list(2, 2)).containsExactly(
            new ProductSummaryView(oldest.id(), category, "Pijama", "pijama", false, 2));
        assertThat(subject.list(3, 2)).isEmpty();
    }

    @Test
    void givenUnknownIds_whenLoading_thenTheyAreEmpty() {
        assertThat(subject.findById(ProductId.of(UUID.randomUUID()))).isEmpty();
        assertThat(subject.findBySlug(new Slug("no-existe"))).isEmpty();
        assertThat(subject.lockById(ProductId.of(UUID.randomUUID()))).isEmpty();
        assertThat(subject.lockByVariantId(VariantId.of(UUID.randomUUID()))).isEmpty();
    }

    private Product product(String name, Instant createdAt) {
        return Product.create(ProductId.of(UUID.randomUUID()), details(name), createdAt);
    }

    private ProductDetails details(String name) {
        return new ProductDetails(category, new ProductName(name), new Description(""), new Material("Algodón"),
            Season.SUMMER, Audience.WOMEN);
    }

    private static ImageId addImage(Product product, Optional<Color> color, int file) {
        var image = new Product.NewImage(ImageId.of(UUID.randomUUID()), color, StorageKey.of(UUID.randomUUID()),
            new ImagePlaceholder("data:image/webp;base64,UklGRg=="), UploadKey.of(UUID.randomUUID()),
            ContentHash.of(new byte[] {(byte) file}));
        return ((Product.ImageUpload.Added) product.addImage(image, CREATED)).image().id();
    }

    /** Generates the matrix with SKUs {@code prefix + n}, or with {@code skus} first when given. */
    private static void generate(Product product, List<String> sizes, List<String> colors, String prefix,
                                 String... skus) {
        Deque<String> queued = new ArrayDeque<>(List.of(skus));
        var identities = new VariantIdentities() {
            private int next;

            @Override
            public VariantId nextId() {
                return VariantId.of(UUID.randomUUID());
            }

            @Override
            public Sku nextSku() {
                return new Sku(queued.isEmpty() ? prefix + (++next) + "-" + product.id().value().toString()
                    .substring(0, 8) : queued.poll());
            }
        };
        var request = ((VariantMatrix.Parsed.Valid) VariantMatrix.parse(sizes, colors)).request();
        product.generateVariants(request, identities, CREATED);
    }

    private static void assertSameState(Product actual, Product expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.details()).isEqualTo(expected.details());
        assertThat(actual.slug()).isEqualTo(expected.slug());
        assertThat(actual.published()).isEqualTo(expected.published());
        assertThat(actual.createdAt()).isEqualTo(expected.createdAt());
        assertThat(actual.updatedAt()).isEqualTo(expected.updatedAt());
        assertThat(actual.variants()).usingRecursiveFieldByFieldElementComparator()
            .containsExactlyElementsOf(expected.variants());
        assertThat(actual.images()).usingRecursiveFieldByFieldElementComparator()
            .containsExactlyElementsOf(expected.images());
    }
}
