package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CATEGORY;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static com.pijamaparty.store.catalog.application.ProductFixture.NOW;
import static com.pijamaparty.store.catalog.application.ProductFixture.details;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.CreateProduct.Command;
import com.pijamaparty.store.catalog.application.CreateProduct.Result;
import com.pijamaparty.store.catalog.application.ProductDetailsInput.Field;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateProductServiceTest {

    private final ProductFixture fixture = new ProductFixture();

    @Test
    void givenDetails_whenCreatingAProduct_thenItIsHiddenWithoutVariantsAndItsSlugComesFromTheName() {
        Result result = fixture.create.execute(new Command(ADMIN, new ProductDetailsInput(CATEGORY,
            "  Pijama de Algodón ", " Suave.\nFresco. ", " Algodón ", Season.WINTER, Audience.KIDS)));

        ProductId id = ProductId.of(InMemoryIdGenerator.nth(1));
        assertThat(result).isEqualTo(new Result.Created(new ProductView(id, CATEGORY, "Pijama de Algodón",
            "pijama-de-algodon", "Suave.\nFresco.", "Algodón", Season.WINTER, Audience.KIDS, false, List.of(),
            List.of(), List.of(), List.of())));
        assertThat(fixture.products.findById(id)).get()
            .satisfies(saved -> assertThat(saved.createdAt()).isEqualTo(NOW));
    }

    @Test
    void givenAnExistingProductName_whenAnotherWithTheSameSlugIsCreated_thenItIsRejected() {
        fixture.created("Pijama de Algodón");

        Result result = fixture.create.execute(new Command(ADMIN, details("PIJAMA DE ALGODON")));

        assertThat(result).isEqualTo(new Result.SlugAlreadyUsed("pijama-de-algodon"));
        assertThat(fixture.products.list(0, 10)).hasSize(1);
    }

    @Test
    void givenInvalidFields_whenCreatingAProduct_thenEveryInvalidOneIsNamed() {
        Result result = fixture.create.execute(new Command(ADMIN,
            new ProductDetailsInput(null, " ¡! ", "a".repeat(5001), "Algo\ndón", null, null)));

        assertThat(result).isEqualTo(new Result.InvalidDetails(Set.of(Field.CATEGORY, Field.NAME, Field.DESCRIPTION,
            Field.MATERIAL, Field.SEASON, Field.AUDIENCE)));
        assertThat(fixture.products.list(0, 10)).isEmpty();
    }

    @Test
    void givenAnUnknownCategory_whenCreatingAProduct_thenItIsRejected() {
        Result result = fixture.create.execute(new Command(ADMIN, new ProductDetailsInput(
            CategoryId.of(UUID.randomUUID()), "Pijama", "", "Algodón", Season.SUMMER, Audience.WOMEN)));

        assertThat(result).isEqualTo(new Result.UnknownCategory());
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenCreatingAProduct_thenItIsForbidden() {
        assertThat(fixture.create.execute(new Command(CUSTOMER, details("Pijama")))).isEqualTo(new Result.Forbidden());
        assertThat(fixture.create.execute(new Command(Actor.system(), details("Pijama"))))
            .isEqualTo(new Result.Forbidden());
        assertThat(fixture.products.list(0, 10)).isEmpty();
    }
}
