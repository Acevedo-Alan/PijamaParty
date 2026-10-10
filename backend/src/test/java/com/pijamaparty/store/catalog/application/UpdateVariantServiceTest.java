package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.UpdateVariant.Command;
import com.pijamaparty.store.catalog.application.UpdateVariant.Result;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateVariantServiceTest {

    private final ProductFixture fixture = new ProductFixture();

    @Test
    void givenAVariant_whenItsPriceIsSet_thenTheProductShowsIt() {
        ProductView product = fixture.withMatrix("Pijama", List.of("S", "M"), List.of("Rosa"));
        VariantId variant = product.variants().getFirst().id();

        Result result = fixture.updateVariant.execute(new Command(ADMIN, variant, Optional.of(1_999_900L),
            Optional.empty()));

        assertThat(result).isInstanceOfSatisfying(Result.Updated.class, updated ->
            assertThat(updated.product().variants().getFirst().price()).contains(Money.of(1_999_900)));
        assertThat(fixture.products.findById(product.id()).orElseThrow().variants().getFirst().price())
            .contains(Money.of(1_999_900));
    }

    @Test
    void givenAPriceOutOfRange_whenSettingIt_thenItIsRejected() {
        VariantId variant = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa")).variants().getFirst().id();

        for (long cents : new long[] {0, -1, Long.MIN_VALUE, 1_000_000_001L}) {
            assertThat(fixture.updateVariant.execute(new Command(ADMIN, variant, Optional.of(cents), Optional.empty())))
                .as("%d cents", cents).isEqualTo(new Result.InvalidPrice());
        }
    }

    @Test
    void givenAPublishedProduct_whenItsLastActivePricedVariantIsDeactivated_thenItIsRejected() {
        ProductView product = fixture.withMatrix("Pijama", List.of("S", "M"), List.of("Rosa"));
        ProductView.VariantView priced = product.variants().getFirst();
        fixture.priced(priced, 1_500_000);
        fixture.publish.execute(new PublishProduct.Command(ADMIN, product.id()));

        Result result = fixture.updateVariant.execute(new Command(ADMIN, priced.id(), Optional.empty(),
            Optional.of(false)));

        assertThat(result).isEqualTo(new Result.LastActivePricedVariant());
        assertThat(fixture.products.findById(product.id()).orElseThrow().variants().getFirst().active()).isTrue();
        assertThat(fixture.updateVariant.execute(new Command(ADMIN, product.variants().get(1).id(), Optional.empty(),
            Optional.of(false)))).isInstanceOf(Result.Updated.class);
    }

    @Test
    void givenAnUnknownVariantOrAnotherActor_whenUpdating_thenItIsRejected() {
        VariantId variant = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa")).variants().getFirst().id();

        assertThat(fixture.updateVariant.execute(new Command(ADMIN, VariantId.of(UUID.randomUUID()),
            Optional.of(100L), Optional.empty()))).isEqualTo(new Result.NotFound());
        assertThat(fixture.updateVariant.execute(new Command(CUSTOMER, variant, Optional.of(100L), Optional.empty())))
            .isEqualTo(new Result.Forbidden());
    }
}
