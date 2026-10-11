package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.catalog.domain.Product.MatrixOutcome;
import com.pijamaparty.store.catalog.domain.Product.PublishOutcome;
import com.pijamaparty.store.catalog.domain.Product.VariantOutcome;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T12:00:00Z");

    private final VariantIdentities identities = new SequentialIdentities();

    @Test
    void givenDetails_whenAProductIsCreated_thenItIsHiddenWithoutVariantsAndItsSlugComesFromTheName() {
        Product product = product("Pijama de Algodón");

        assertThat(product.published()).isFalse();
        assertThat(product.variants()).isEmpty();
        assertThat(product.slug()).isEqualTo(new Slug("pijama-de-algodon"));
        assertThat(product.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void givenAProduct_whenItsNameChanges_thenTheSlugFollowsIt() {
        Product product = product("Pijama de Algodón");

        product.update(details("Pijama de Seda"), LATER);

        assertThat(product.slug()).isEqualTo(new Slug("pijama-de-seda"));
        assertThat(product.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenTheSameDetails_whenUpdating_thenNothingChanges() {
        Product product = product("Pijama de Algodón");

        product.update(details("Pijama de Algodón"), LATER);

        assertThat(product.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void given3SizesAnd2Colors_whenTheMatrixIsGenerated_then6VariantsAreCreated() {
        Product product = product("Pijama");

        MatrixOutcome outcome = product.generateVariants(request(List.of("S", "M", "L"), List.of("Rosa", "Celeste")),
            identities, LATER);

        assertThat(outcome).isInstanceOfSatisfying(MatrixOutcome.Generated.class,
            generated -> assertThat(generated.created()).hasSize(6));
        assertThat(product.variants()).hasSize(6)
            .allSatisfy(variant -> {
                assertThat(variant.active()).isTrue();
                assertThat(variant.price()).isEmpty();
            })
            .extracting(variant -> variant.size().value() + "/" + variant.color().value())
            .containsExactly("S/Rosa", "S/Celeste", "M/Rosa", "M/Celeste", "L/Rosa", "L/Celeste");
        assertThat(product.variants()).extracting(Variant::sku).doesNotHaveDuplicates();
        assertThat(product.sizes()).extracting(Size::value).containsExactly("S", "M", "L");
        assertThat(product.colors()).extracting(Color::value).containsExactly("Rosa", "Celeste");
    }

    @Test
    void givenAGeneratedMatrix_whenTheSameIsGeneratedAgain_thenNothingIsCreated() {
        Product product = product("Pijama");
        product.generateVariants(request(List.of("S", "M"), List.of("Rosa")), identities, LATER);
        List<Variant> before = product.variants();

        MatrixOutcome outcome = product.generateVariants(request(List.of("S", "M"), List.of("Rosa")), identities,
            LATER);

        assertThat(outcome).isEqualTo(new MatrixOutcome.Generated(List.of()));
        assertThat(product.variants()).usingRecursiveFieldByFieldElementComparator().isEqualTo(before);
    }

    @Test
    void givenExistingVariants_whenNewSizesAndColorsAreGenerated_thenOnlyMissingOnesAreCreatedAndRequestedOnesGoFirst() {
        Product product = product("Pijama");
        product.generateVariants(request(List.of("S", "M"), List.of("Rosa")), identities, CREATED);

        MatrixOutcome outcome = product.generateVariants(request(List.of("XS", "M"), List.of("Celeste", "Rosa")),
            identities, LATER);

        assertThat(outcome).isInstanceOfSatisfying(MatrixOutcome.Generated.class, generated ->
            assertThat(generated.created()).extracting(variant -> variant.size().value() + "/" + variant.color().value())
                .containsExactly("XS/Celeste", "XS/Rosa", "M/Celeste"));
        assertThat(product.sizes()).extracting(Size::value).containsExactly("XS", "M", "S");
        assertThat(product.colors()).extracting(Color::value).containsExactly("Celeste", "Rosa");
        assertThat(product.variants()).hasSize(5);
    }

    @Test
    void givenAProductWith20Sizes_whenAnotherSizeIsGenerated_thenItIsRejectedAndNothingChanges() {
        Product product = product("Pijama");
        product.generateVariants(request(values("T", 20), List.of("Rosa")), identities, CREATED);

        MatrixOutcome outcome = product.generateVariants(request(List.of("XXL"), List.of("Rosa")), identities, LATER);

        assertThat(outcome).isEqualTo(new MatrixOutcome.Rejected(VariantMatrix.Problem.TOO_MANY_SIZES));
        assertThat(product.variants()).hasSize(20);
        assertThat(product.generateVariants(request(List.of("T1"), values("C", 20)), identities, LATER))
            .isEqualTo(new MatrixOutcome.Rejected(VariantMatrix.Problem.TOO_MANY_COLORS));
    }

    @Test
    void givenAVariant_whenItsPriceIsSet_thenItCanChangeButStaysWithinRange() {
        Product product = withVariants();
        VariantId id = product.variants().getFirst().id();

        assertThat(product.changeVariant(id, Optional.of(Money.of(1_999_900)), Optional.empty(), LATER))
            .isEqualTo(new VariantOutcome.Changed());
        assertThat(product.variants().getFirst().price()).contains(Money.of(1_999_900));
        assertThat(product.variants().getFirst().updatedAt()).isEqualTo(LATER);

        assertThat(product.changeVariant(id, Optional.of(Money.zero()), Optional.empty(), LATER))
            .isEqualTo(new VariantOutcome.InvalidPrice());
        assertThat(product.changeVariant(id, Optional.of(Money.of(1_000_000_001L)), Optional.of(false), LATER))
            .isEqualTo(new VariantOutcome.InvalidPrice());
        assertThat(product.changeVariant(id, Optional.of(Variant.MAX_PRICE), Optional.empty(), LATER))
            .isEqualTo(new VariantOutcome.Changed());
        assertThat(product.variants().getFirst().active()).isTrue();
    }

    @Test
    void givenCentsAsSent_whenReadAsAPrice_thenOnly1To1000000000Count() {
        assertThat(Variant.priceOf(1)).contains(Money.of(1));
        assertThat(Variant.priceOf(1_000_000_000L)).contains(Variant.MAX_PRICE);
        assertThat(Variant.priceOf(0)).isEmpty();
        assertThat(Variant.priceOf(-1)).isEmpty();
        assertThat(Variant.priceOf(Long.MIN_VALUE)).isEmpty();
        assertThat(Variant.priceOf(1_000_000_001L)).isEmpty();
    }

    @Test
    void givenAnUnknownVariant_whenChangingIt_thenItIsReported() {
        assertThat(withVariants().changeVariant(VariantId.of(UUID.randomUUID()), Optional.empty(), Optional.of(false),
            LATER)).isEqualTo(new VariantOutcome.UnknownVariant());
    }

    @Test
    void givenAHiddenProductWithAnActivePricedVariant_whenPublished_thenItIsPublished() {
        Product product = withVariants();
        product.changeVariant(product.variants().getFirst().id(), Optional.of(Money.of(1_500_000)), Optional.empty(),
            LATER);

        assertThat(product.publish(LATER)).isEqualTo(new PublishOutcome.Published());

        assertThat(product.published()).isTrue();
        assertThat(product.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenAProductWithoutAnActivePricedVariant_whenPublished_thenItStaysHidden() {
        Product product = withVariants();
        VariantId first = product.variants().getFirst().id();
        assertThat(product.publish(LATER)).isEqualTo(new PublishOutcome.NoActivePricedVariant());

        product.changeVariant(first, Optional.of(Money.of(1_500_000)), Optional.of(false), LATER);

        assertThat(product.publish(LATER)).isEqualTo(new PublishOutcome.NoActivePricedVariant());
        assertThat(product.published()).isFalse();
        assertThat(product(" Sin variantes").publish(LATER)).isEqualTo(new PublishOutcome.NoActivePricedVariant());
    }

    @Test
    void givenAPublishedProduct_whenItsLastActivePricedVariantIsDeactivated_thenItIsRejectedUntilHidden() {
        Product product = withVariants();
        VariantId priced = product.variants().getFirst().id();
        VariantId unpriced = product.variants().get(1).id();
        product.changeVariant(priced, Optional.of(Money.of(1_500_000)), Optional.empty(), LATER);
        product.publish(LATER);

        assertThat(product.changeVariant(unpriced, Optional.empty(), Optional.of(false), LATER))
            .isEqualTo(new VariantOutcome.Changed());
        assertThat(product.changeVariant(priced, Optional.of(Money.of(1_600_000)), Optional.of(false), LATER))
            .isEqualTo(new VariantOutcome.LastActivePricedVariant());
        assertThat(product.variants().getFirst().price()).contains(Money.of(1_500_000));

        product.hide(LATER);
        assertThat(product.published()).isFalse();
        assertThat(product.changeVariant(priced, Optional.empty(), Optional.of(false), LATER))
            .isEqualTo(new VariantOutcome.Changed());
        assertThat(product.changeVariant(priced, Optional.empty(), Optional.of(true), LATER))
            .isEqualTo(new VariantOutcome.Changed());
    }

    @Test
    void givenAPublishedProduct_whenPublishedAgainOrAHiddenOneIsHidden_thenNothingChanges() {
        Product hidden = product("Pijama");
        hidden.hide(LATER);
        assertThat(hidden.updatedAt()).isEqualTo(CREATED);

        Product published = withVariants();
        published.changeVariant(published.variants().getFirst().id(), Optional.of(Money.of(100)), Optional.empty(),
            CREATED);
        published.publish(CREATED);
        assertThat(published.publish(LATER)).isEqualTo(new PublishOutcome.Published());
        assertThat(published.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void givenAPublishedProduct_whenAskedWhetherVisitorsSeeIt_thenOnlyAnActiveCategoryShowsItsSellableVariants() {
        Category category = Category.create(CategoryId.of(new UUID(0, 1)), new CategoryName("Verano"), List.of(),
            CREATED);
        Product product = withVariants();
        VariantId small = product.variants().getFirst().id();
        product.changeVariant(small, Optional.of(Money.of(100)), Optional.empty(), CREATED);
        assertThat(product.isListedIn(category)).isFalse();

        product.publish(CREATED);

        assertThat(product.isListedIn(category)).isTrue();
        assertThat(product.sellableVariants()).extracting(Variant::id).containsExactly(small);
        category.deactivate(LATER);
        assertThat(product.isListedIn(category)).isFalse();
        Category other = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Otra"), List.of(), CREATED);
        assertThatThrownBy(() -> product.isListedIn(other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenACopy_whenTheOriginalChanges_thenTheCopyDoesNot() {
        Product product = withVariants();
        Product copy = product.copy();

        product.changeVariant(product.variants().getFirst().id(), Optional.of(Money.of(100)), Optional.empty(), LATER);
        product.update(details("Otro nombre"), LATER);

        assertThat(copy.variants().getFirst().price()).isEmpty();
        assertThat(copy.slug()).isEqualTo(new Slug("pijama"));
    }

    private Product withVariants() {
        Product product = product("Pijama");
        product.generateVariants(request(List.of("S", "M"), List.of("Rosa")), identities, CREATED);
        return product;
    }

    private static Product product(String name) {
        return Product.create(ProductId.of(UUID.randomUUID()), details(name.strip()), CREATED);
    }

    static ProductDetails details(String name) {
        return new ProductDetails(CategoryId.of(new UUID(0, 1)), new ProductName(name), new Description(""),
            new Material("Algodón"), Season.SUMMER, Audience.WOMEN);
    }

    private static VariantMatrix.Request request(List<String> sizes, List<String> colors) {
        return ((VariantMatrix.Parsed.Valid) VariantMatrix.parse(sizes, colors)).request();
    }

    private static List<String> values(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> prefix + i).toList();
    }

    /** Ids and SKUs in sequence, so assertions are deterministic. */
    static final class SequentialIdentities implements VariantIdentities {

        private long next;

        @Override
        public VariantId nextId() {
            return VariantId.of(new UUID(1, ++next));
        }

        @Override
        public Sku nextSku() {
            return new Sku("PP-T" + next);
        }
    }
}
