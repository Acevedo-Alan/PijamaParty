package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.ADMIN;
import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.ReorderCategories.Command;
import com.pijamaparty.store.catalog.application.ReorderCategories.Result;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReorderCategoriesServiceTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final ReorderCategoriesService service = new ReorderCategoriesService(categories, categories,
        new InMemoryCatalogActivity(),
        Clock.fixed(NOW, ZoneOffset.UTC));
    private final List<Category> existing = new ArrayList<>();
    private Category summer;
    private Category winter;
    private Category kids;

    @BeforeEach
    void givenThreeCategories() {
        summer = create("Verano");
        winter = create("Invierno");
        kids = create("Niños");
    }

    @Test
    void givenEveryCategoryInANewOrder_whenReordering_thenTheyAreSavedAndReturnedInThatOrder() {
        Result result = service.execute(new Command(ADMIN, List.of(kids.id(), summer.id(), winter.id())));

        assertThat(result).isEqualTo(new Result.Reordered(List.of(
            new CategoryView(kids.id(), "Niños", "ninos", 0, true),
            new CategoryView(summer.id(), "Verano", "verano", 1, true),
            new CategoryView(winter.id(), "Invierno", "invierno", 2, true))));
        assertThat(categories.findAll()).extracting(Category::id).containsExactly(kids.id(), summer.id(), winter.id());
    }

    @Test
    void givenAnOrderThatDoesNotNameEveryCategoryOnce_whenReordering_thenItIsRejectedAndNothingMoves() {
        Result result = service.execute(new Command(ADMIN, List.of(kids.id(), summer.id())));

        assertThat(result).isEqualTo(new Result.InvalidOrder());
        assertThat(categories.findAll()).extracting(Category::id).containsExactly(summer.id(), winter.id(), kids.id());
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenReordering_thenItIsForbidden() {
        assertThat(service.execute(new Command(Actor.system(), List.of(kids.id(), summer.id(), winter.id()))))
            .isEqualTo(new Result.Forbidden());
        assertThat(categories.findAll()).extracting(Category::id).containsExactly(summer.id(), winter.id(), kids.id());
    }

    private Category create(String name) {
        Category category = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName(name), existing,
            NOW.minusSeconds(3600));
        existing.add(category);
        categories.save(category);
        return category;
    }
}
