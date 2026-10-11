package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.details;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.api.CategoryRenamed;
import com.pijamaparty.store.catalog.api.ProductChanged;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** UC-26: the writes that change searchable texts publish the event that refreshes the index; failed ones do not. */
class CatalogEventsPublicationTest {

    private final ProductFixture fixture = new ProductFixture();

    @Test
    void givenAProduct_whenCreatedEditedGivenVariantsAndAVariantChanges_thenEachWritePublishesProductChanged() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));
        fixture.update.execute(new UpdateProduct.Command(ADMIN, product.id(), details("Pijama de algodón")));
        fixture.priced(product.variants().getFirst(), 1000);

        assertThat(fixture.events.published()).containsOnly(new ProductChanged(product.id().value())).hasSize(4);
    }

    @Test
    void givenRejectedWrites_whenAttempted_thenNothingIsPublished() {
        ProductView product = fixture.created("Pijama");
        ProductView other = fixture.created("Camisón");
        int before = fixture.events.published().size();

        fixture.create.execute(new CreateProduct.Command(ADMIN, details("Pijama")));
        fixture.update.execute(new UpdateProduct.Command(ADMIN, other.id(), details("Pijama")));
        fixture.update.execute(new UpdateProduct.Command(ProductFixture.CUSTOMER, product.id(), details("Otro")));
        fixture.generate.execute(new GenerateVariantMatrix.Command(ADMIN, product.id(), List.of(), List.of("Rosa")));

        assertThat(fixture.events.published()).hasSize(before);
    }

    @Test
    void givenACategory_whenRenamed_thenCategoryRenamedIsPublished() {
        InMemoryCatalogEvents events = new InMemoryCatalogEvents();
        var rename = new RenameCategoryService(fixture.categories, fixture.categories, events, fixture.clock);

        rename.execute(new RenameCategory.Command(ADMIN, ProductFixture.CATEGORY, "Noche"));
        rename.execute(new RenameCategory.Command(ADMIN, ProductFixture.CATEGORY, ""));

        assertThat(events.published()).containsExactly(new CategoryRenamed(ProductFixture.CATEGORY.value()));
    }

    @Test
    void givenAVariant_whenDeactivated_thenProductChangedIsPublishedForItsColors() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));
        int before = fixture.events.published().size();

        fixture.updateVariant.execute(new UpdateVariant.Command(ADMIN, product.variants().getFirst().id(),
            Optional.empty(), Optional.of(false)));

        assertThat(fixture.events.published()).hasSize(before + 1);
    }
}
