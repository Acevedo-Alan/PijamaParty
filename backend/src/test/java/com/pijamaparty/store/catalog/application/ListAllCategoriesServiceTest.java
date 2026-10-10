package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.ADMIN;
import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.ListAllCategories.Query;
import com.pijamaparty.store.catalog.application.ListAllCategories.Result;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListAllCategoriesServiceTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final ListAllCategoriesService service = new ListAllCategoriesService(categories);

    @Test
    void givenActiveAndInactiveCategories_whenTheAdministratorListsThem_thenAllComeByPosition() {
        Category summer = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Verano"), List.of(), NOW);
        Category winter = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Invierno"),
            List.of(summer), NOW);
        summer.deactivate(NOW);
        categories.save(winter);
        categories.save(summer);

        assertThat(service.execute(new Query(ADMIN))).isEqualTo(new Result.Listed(List.of(
            new CategoryView(summer.id(), "Verano", "verano", 0, false),
            new CategoryView(winter.id(), "Invierno", "invierno", 1, true))));
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenListing_thenItIsForbidden() {
        assertThat(service.execute(new Query(Actor.anonymous(Optional.empty(), Optional.empty()))))
            .isEqualTo(new Result.Forbidden());
    }
}
