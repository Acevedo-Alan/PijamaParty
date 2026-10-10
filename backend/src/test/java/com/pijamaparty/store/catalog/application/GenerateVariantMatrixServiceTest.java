package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.catalog.application.GenerateVariantMatrix.Command;
import com.pijamaparty.store.catalog.application.GenerateVariantMatrix.Result;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.VariantMatrix.Problem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerateVariantMatrixServiceTest {

    private final ProductFixture fixture = new ProductFixture();

    @Test
    void given3SizesAnd2Colors_whenTheMatrixIsGenerated_then6VariantsAreCreatedEachWithItsStockLevel() {
        ProductView product = fixture.created("Pijama");

        Result result = fixture.generate.execute(new Command(ADMIN, product.id(), List.of("S", "M", "L"),
            List.of("Rosa", "Celeste")));

        assertThat(result).isInstanceOfSatisfying(Result.Generated.class, generated -> {
            assertThat(generated.created()).isEqualTo(6);
            assertThat(generated.product().variants()).hasSize(6)
                .allSatisfy(variant -> {
                    assertThat(variant.active()).isTrue();
                    assertThat(variant.price()).isEmpty();
                    assertThat(fixture.stockLevels.onHandOf(variant.id())).contains(0);
                });
            assertThat(generated.product().sizes()).containsExactly("S", "M", "L");
            assertThat(generated.product().colors()).containsExactly("Rosa", "Celeste");
        });
        assertThat(fixture.products.findById(product.id()).orElseThrow().variants()).hasSize(6);
    }

    @Test
    void givenAGeneratedMatrix_whenTheSameRequestIsRepeated_thenNothingNewIsCreated() {
        ProductView first = fixture.withMatrix("Pijama", List.of("S", "M"), List.of("Rosa"));

        Result repeated = fixture.generate.execute(new Command(ADMIN, first.id(), List.of("S", "M"), List.of("Rosa")));

        assertThat(repeated).isEqualTo(new Result.Generated(first, 0));
        assertThat(fixture.stockLevels.opened()).isEqualTo(2);
    }

    @Test
    void givenASkuThatClashes_whenGenerating_thenItIsRetriedWithNewSkus() {
        ProductView other = fixture.withMatrix("Camisón", List.of("M"), List.of("Rosa"));
        ProductView product = fixture.created("Pijama");
        fixture.skus.thenReturn(other.variants().getFirst().sku());

        Result result = fixture.generate.execute(new Command(ADMIN, product.id(), List.of("S", "M"),
            List.of("Rosa")));

        assertThat(result).isInstanceOfSatisfying(Result.Generated.class, generated ->
            assertThat(generated.product().variants()).extracting(ProductView.VariantView::sku)
                .doesNotContain(other.variants().getFirst().sku()).hasSize(2));
        assertThat(fixture.stockLevels.opened()).isEqualTo(3);
    }

    @Test
    void givenSkusThatAlwaysClash_whenGenerating_thenItFailsWithoutSavingAnything() {
        ProductView other = fixture.withMatrix("Camisón", List.of("M"), List.of("Rosa"));
        ProductView product = fixture.created("Pijama");
        String used = other.variants().getFirst().sku();
        for (int i = 0; i < GenerateVariantMatrixService.SKU_ATTEMPTS; i++) {
            fixture.skus.thenReturn(used);
        }

        assertThatThrownBy(() -> fixture.generate.execute(new Command(ADMIN, product.id(), List.of("M"),
            List.of("Rosa")))).isInstanceOf(IllegalStateException.class);
        assertThat(fixture.products.findById(product.id()).orElseThrow().variants()).isEmpty();
    }

    @Test
    void givenInvalidSizesOrColors_whenGenerating_thenTheProblemIsNamedAndNothingIsCreated() {
        ProductView product = fixture.created("Pijama");

        assertThat(fixture.generate.execute(new Command(ADMIN, product.id(), List.of("M", "M "), List.of("Rosa"))))
            .isEqualTo(new Result.InvalidMatrix(Problem.REPEATED_SIZES));
        assertThat(fixture.generate.execute(new Command(ADMIN, product.id(), List.of("M"), List.of())))
            .isEqualTo(new Result.InvalidMatrix(Problem.NO_COLORS));
        assertThat(fixture.products.findById(product.id()).orElseThrow().variants()).isEmpty();
        assertThat(fixture.stockLevels.opened()).isZero();
    }

    @Test
    void givenAProductWith20Colors_whenAnotherColorIsGenerated_thenItIsRejected() {
        List<String> colors = java.util.stream.IntStream.rangeClosed(1, 20).mapToObj(i -> "Color " + i).toList();
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), colors);

        assertThat(fixture.generate.execute(new Command(ADMIN, product.id(), List.of("M"), List.of("Negro"))))
            .isEqualTo(new Result.InvalidMatrix(Problem.TOO_MANY_COLORS));
    }

    @Test
    void givenAMissingProductOrAnotherActor_whenGenerating_thenItIsRejected() {
        ProductView product = fixture.created("Pijama");

        assertThat(fixture.generate.execute(new Command(ADMIN, ProductId.of(UUID.randomUUID()), List.of("M"),
            List.of("Rosa")))).isEqualTo(new Result.NotFound());
        assertThat(fixture.generate.execute(new Command(CUSTOMER, product.id(), List.of("M"), List.of("Rosa"))))
            .isEqualTo(new Result.Forbidden());
        assertThat(fixture.stockLevels.opened()).isZero();
    }
}
