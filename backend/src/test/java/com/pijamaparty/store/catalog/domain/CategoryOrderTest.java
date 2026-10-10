package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryOrderTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T12:00:00Z");

    private Category summer;
    private Category winter;
    private Category kids;

    @BeforeEach
    void createCategories() {
        List<Category> existing = new ArrayList<>();
        summer = create("Verano", existing);
        winter = create("Invierno", existing);
        kids = create("Niños", existing);
    }

    @Test
    void givenEveryCategoryInANewOrder_whenApplied_thenEachTakesItsIndexAndOnlyTheMovedOnesChange() {
        var outcome = CategoryOrder.apply(List.of(summer, winter, kids), List.of(kids.id(), winter.id(), summer.id()),
            LATER);

        assertThat(outcome).isInstanceOf(CategoryOrder.Outcome.Reordered.class);
        assertThat(((CategoryOrder.Outcome.Reordered) outcome).changed()).containsExactlyInAnyOrder(kids, summer);
        assertThat(List.of(kids.position(), winter.position(), summer.position())).containsExactly(0, 1, 2);
        assertThat(winter.updatedAt()).isEqualTo(CREATED);
        assertThat(kids.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenAnOrderThatMissesRepeatsOrInventsACategory_whenApplied_thenItIsRejectedAndNothingMoves() {
        List<Category> all = List.of(summer, winter, kids);
        CategoryId unknown = CategoryId.of(UUID.randomUUID());

        assertThat(CategoryOrder.apply(all, List.of(kids.id(), winter.id()), LATER))
            .isEqualTo(new CategoryOrder.Outcome.InvalidOrder());
        assertThat(CategoryOrder.apply(all, List.of(kids.id(), kids.id(), winter.id()), LATER))
            .isEqualTo(new CategoryOrder.Outcome.InvalidOrder());
        assertThat(CategoryOrder.apply(all, List.of(kids.id(), winter.id(), summer.id(), unknown), LATER))
            .isEqualTo(new CategoryOrder.Outcome.InvalidOrder());
        assertThat(List.of(summer.position(), winter.position(), kids.position())).containsExactly(0, 1, 2);
    }

    private static Category create(String name, List<Category> existing) {
        Category category = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName(name), existing,
            CREATED);
        existing.add(category);
        return category;
    }
}
