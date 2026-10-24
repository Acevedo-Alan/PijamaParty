package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.ADMIN;
import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link DeactivateCategoryService}, {@link ActivateCategoryService} and {@link DeleteCategoryService}. */
class CategoryStatusServicesTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DeactivateCategoryService deactivate = new DeactivateCategoryService(categories, categories,
        new InMemoryCatalogActivity(), clock);
    private final ActivateCategoryService activate = new ActivateCategoryService(categories, categories,
        new InMemoryCatalogActivity(), clock);
    private final DeleteCategoryService delete = new DeleteCategoryService(categories, categories,
        new InMemoryCatalogActivity());
    private final CategoryId unknown = CategoryId.of(UUID.randomUUID());
    private Category summer;

    @BeforeEach
    void givenACategory() {
        summer = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Verano"), List.of(),
            NOW.minusSeconds(3600));
        categories.save(summer);
    }

    @Test
    void givenACategoryWithProducts_whenDeletionIsAttempted_thenOnlyDeactivationIsAllowed() {
        categories.addProductTo(summer.id());

        assertThat(delete.execute(new DeleteCategory.Command(ADMIN, summer.id())))
            .isEqualTo(new DeleteCategory.Result.HasProducts());
        assertThat(categories.findById(summer.id())).isPresent();

        assertThat(deactivate.execute(new DeactivateCategory.Command(ADMIN, summer.id())))
            .isEqualTo(new DeactivateCategory.Result.Deactivated(
                new CategoryView(summer.id(), "Verano", "verano", 0, false)));
        assertThat(categories.findById(summer.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void givenACategoryWithoutProducts_whenDeleted_thenItIsGone() {
        assertThat(delete.execute(new DeleteCategory.Command(ADMIN, summer.id())))
            .isEqualTo(new DeleteCategory.Result.Deleted());
        assertThat(categories.findById(summer.id())).isEmpty();
    }

    @Test
    void givenADeactivatedCategory_whenActivated_thenItIsActiveAgain() {
        deactivate.execute(new DeactivateCategory.Command(ADMIN, summer.id()));

        assertThat(activate.execute(new ActivateCategory.Command(ADMIN, summer.id())))
            .isEqualTo(new ActivateCategory.Result.Activated(
                new CategoryView(summer.id(), "Verano", "verano", 0, true)));
        assertThat(categories.findById(summer.id()).orElseThrow().active()).isTrue();
    }

    @Test
    void givenAnUnknownCategory_whenChangingIt_thenItIsNotFound() {
        assertThat(deactivate.execute(new DeactivateCategory.Command(ADMIN, unknown)))
            .isEqualTo(new DeactivateCategory.Result.NotFound());
        assertThat(activate.execute(new ActivateCategory.Command(ADMIN, unknown)))
            .isEqualTo(new ActivateCategory.Result.NotFound());
        assertThat(delete.execute(new DeleteCategory.Command(ADMIN, unknown)))
            .isEqualTo(new DeleteCategory.Result.NotFound());
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenChangingACategory_thenItIsForbidden() {
        Actor system = Actor.system();

        assertThat(deactivate.execute(new DeactivateCategory.Command(system, summer.id())))
            .isEqualTo(new DeactivateCategory.Result.Forbidden());
        assertThat(activate.execute(new ActivateCategory.Command(system, summer.id())))
            .isEqualTo(new ActivateCategory.Result.Forbidden());
        assertThat(delete.execute(new DeleteCategory.Command(system, summer.id())))
            .isEqualTo(new DeleteCategory.Result.Forbidden());
        assertThat(categories.findById(summer.id()).orElseThrow().active()).isTrue();
    }
}
