package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static com.pijamaparty.store.catalog.application.ProductFixture.details;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.ProductDetailsInput.Field;
import com.pijamaparty.store.catalog.application.UpdateProduct.Command;
import com.pijamaparty.store.catalog.application.UpdateProduct.Result;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Season;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateProductServiceTest {

    private final ProductFixture fixture = new ProductFixture();

    @Test
    void givenAProduct_whenItsDetailsChange_thenTheyAreSavedAndTheSlugFollowsTheName() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));

        Result result = fixture.update.execute(new Command(ADMIN, product.id(), new ProductDetailsInput(
            product.categoryId(), "Pijama de Seda", "Nueva.", "Seda", Season.WINTER, Audience.MEN)));

        assertThat(result).isInstanceOfSatisfying(Result.Updated.class, updated -> {
            assertThat(updated.product().slug()).isEqualTo("pijama-de-seda");
            assertThat(updated.product().material()).isEqualTo("Seda");
            assertThat(updated.product().variants()).isEqualTo(product.variants());
        });
        assertThat(fixture.products.findById(product.id()).orElseThrow().slug().value()).isEqualTo("pijama-de-seda");
    }

    @Test
    void givenAnotherProductsName_whenRenaming_thenItIsRejectedAndNothingChanges() {
        fixture.created("Camisón");
        ProductView product = fixture.created("Pijama");

        Result result = fixture.update.execute(new Command(ADMIN, product.id(), details("camisón")));

        assertThat(result).isEqualTo(new Result.SlugAlreadyUsed("camison"));
        assertThat(fixture.products.findById(product.id()).orElseThrow().slug().value()).isEqualTo("pijama");
    }

    @Test
    void givenInvalidDetailsOrAMissingProductOrCategory_whenUpdating_thenItIsRejected() {
        ProductView product = fixture.created("Pijama");

        assertThat(fixture.update.execute(new Command(ADMIN, product.id(), details(""))))
            .isEqualTo(new Result.InvalidDetails(Set.of(Field.NAME)));
        assertThat(fixture.update.execute(new Command(ADMIN, ProductId.of(UUID.randomUUID()), details("Bata"))))
            .isEqualTo(new Result.NotFound());
        assertThat(fixture.update.execute(new Command(ADMIN, product.id(), new ProductDetailsInput(
            CategoryId.of(UUID.randomUUID()), "Pijama", "", "Algodón", Season.SUMMER, Audience.WOMEN))))
            .isEqualTo(new Result.UnknownCategory());
        assertThat(fixture.update.execute(new Command(CUSTOMER, product.id(), details("Bata"))))
            .isEqualTo(new Result.Forbidden());
    }
}
