package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.CreateCategory.Command;
import com.pijamaparty.store.catalog.application.CreateCategory.Result;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCategoryServiceTest {

    static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final CreateCategoryService service = new CreateCategoryService(categories, categories,
        new InMemoryCatalogActivity(),
        new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAName_whenCreatingACategory_thenItIsActiveLastAndItsSlugComesFromTheName() {
        service.execute(new Command(ADMIN, "Verano"));

        Result result = service.execute(new Command(ADMIN, "  Pijamas de Niñas "));

        CategoryId id = CategoryId.of(InMemoryIdGenerator.nth(2));
        assertThat(result).isEqualTo(new Result.Created(new CategoryView(id, "Pijamas de Niñas", "pijamas-de-ninas", 1,
            true)));
        assertThat(categories.findById(id)).get().satisfies(saved -> assertThat(saved.createdAt()).isEqualTo(NOW));
    }

    @Test
    void givenAnExistingCategoryName_whenAnotherWithTheSameSlugIsCreated_thenItIsRejectedWithAClearMessage() {
        service.execute(new Command(ADMIN, "Pijamas de Niñas"));

        Result result = service.execute(new Command(ADMIN, "pijamas de niñas!"));

        assertThat(result).isEqualTo(new Result.SlugAlreadyUsed("pijamas-de-ninas"));
        assertThat(categories.findAll()).hasSize(1);
    }

    @Test
    void givenANameWithoutLettersOrDigits_whenCreatingACategory_thenItIsRejected() {
        assertThat(service.execute(new Command(ADMIN, " ¡! "))).isEqualTo(new Result.InvalidName());
        assertThat(service.execute(new Command(ADMIN, null))).isEqualTo(new Result.InvalidName());
        assertThat(categories.findAll()).isEmpty();
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenCreatingACategory_thenItIsForbidden() {
        Actor customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(service.execute(new Command(customer, "Verano"))).isEqualTo(new Result.Forbidden());
        assertThat(service.execute(new Command(Actor.system(), "Verano"))).isEqualTo(new Result.Forbidden());
        assertThat(categories.findAll()).isEmpty();
    }
}
