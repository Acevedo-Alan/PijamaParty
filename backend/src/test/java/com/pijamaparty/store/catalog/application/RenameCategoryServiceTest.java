package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.ADMIN;
import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.RenameCategory.Command;
import com.pijamaparty.store.catalog.application.RenameCategory.Result;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenameCategoryServiceTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryCatalogEvents events = new InMemoryCatalogEvents();
    private final RenameCategoryService service = new RenameCategoryService(categories, categories, events,
        Clock.fixed(NOW, ZoneOffset.UTC));
    private Category summer;
    private Category winter;

    @BeforeEach
    void givenTwoCategories() {
        Instant before = NOW.minusSeconds(3600);
        summer = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Verano"), List.of(), before);
        winter = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Invierno"), List.of(summer),
            before);
        categories.save(summer);
        categories.save(winter);
    }

    @Test
    void givenACategory_whenRenamed_thenItsSlugIsRegeneratedFromTheNewName() {
        Result result = service.execute(new Command(ADMIN, summer.id(), " Pijamas de verano "));

        assertThat(result).isEqualTo(new Result.Renamed(new CategoryView(summer.id(), "Pijamas de verano",
            "pijamas-de-verano", 0, true)));
        assertThat(categories.findById(summer.id()).orElseThrow().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAnExistingCategoryName_whenAnotherIsRenamedToTheSameSlug_thenItIsRejectedAndKeepsItsName() {
        Result result = service.execute(new Command(ADMIN, winter.id(), "VERANO"));

        assertThat(result).isEqualTo(new Result.SlugAlreadyUsed("verano"));
        assertThat(categories.findById(winter.id()).orElseThrow().name().value()).isEqualTo("Invierno");
    }

    @Test
    void givenACategory_whenRenamedOnlyInCapitals_thenItKeepsItsOwnSlug() {
        assertThat(service.execute(new Command(ADMIN, summer.id(), "VERANO")))
            .isEqualTo(new Result.Renamed(new CategoryView(summer.id(), "VERANO", "verano", 0, true)));
    }

    @Test
    void givenAnInvalidNameOrAnUnknownCategory_whenRenaming_thenItIsRejected() {
        assertThat(service.execute(new Command(ADMIN, summer.id(), "   "))).isEqualTo(new Result.InvalidName());
        assertThat(service.execute(new Command(ADMIN, CategoryId.of(UUID.randomUUID()), "Otoño")))
            .isEqualTo(new Result.NotFound());
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenRenaming_thenItIsForbidden() {
        Actor visitor = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThat(service.execute(new Command(visitor, summer.id(), "Otoño"))).isEqualTo(new Result.Forbidden());
        assertThat(categories.findById(summer.id()).orElseThrow().name().value()).isEqualTo("Verano");
    }
}
