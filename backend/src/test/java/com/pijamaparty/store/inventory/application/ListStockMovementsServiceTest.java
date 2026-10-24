package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.inventory.api.InMemoryVariantSkus;
import com.pijamaparty.store.inventory.application.ListStockMovements.Query;
import com.pijamaparty.store.inventory.application.ListStockMovements.Result;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListStockMovementsServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);
    private static final VariantId VARIANT = VariantId.of(UUID.randomUUID());

    private final InMemoryStockStore stock = new InMemoryStockStore();
    private final InMemoryVariantSkus skus = new InMemoryVariantSkus();
    private final InMemoryStockActivity activity = new InMemoryStockActivity();
    private final InMemoryUserNameLookup userNames = new InMemoryUserNameLookup();
    private final AdjustStockService adjust = new AdjustStockService(stock, stock, skus, activity,
        new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final ListStockMovementsService service = new ListStockMovementsService(stock, userNames);

    @BeforeEach
    void openVariant() {
        stock.open(VARIANT, OPENED);
    }

    @Test
    void givenAValidAdjustment_whenSaved_thenTheHistoryShowsDateUserAmountAndReason() {
        userNames.add(ADMIN_ID, "Dueña");
        adjust(4, "Reposición");

        Result result = service.execute(new Query(ADMIN, VARIANT, 0));

        assertThat(result).isEqualTo(new Result.Listed(4, List.of(new StockMovementView(
            MovementId.of(InMemoryIdGenerator.nth(1)), MovementType.INITIAL_LOAD, 4, Optional.of("Reposición"),
            Optional.of(ADMIN_ID), Optional.of("Dueña"), NOW)), false));
    }

    @Test
    void givenAUserWhoseNameIsNotVisible_whenListing_thenTheMovementHasNoName() {
        adjust(4, null);

        assertThat(service.execute(new Query(ADMIN, VARIANT, 0)))
            .isInstanceOfSatisfying(Result.Listed.class, listed -> assertThat(listed.movements()).singleElement()
                .satisfies(movement -> {
                    assertThat(movement.userId()).contains(ADMIN_ID);
                    assertThat(movement.userName()).isEmpty();
                    assertThat(movement.note()).isEmpty();
                }));
    }

    @Test
    void givenMoreThan50Movements_whenListing_thenTheNewestComeFirst50PerPage() {
        for (int i = 1; i <= 51; i++) {
            adjust(1, null);
        }

        Result first = service.execute(new Query(ADMIN, VARIANT, 0));
        Result second = service.execute(new Query(ADMIN, VARIANT, 1));

        assertThat(first).isInstanceOfSatisfying(Result.Listed.class, listed -> {
            assertThat(listed.onHand()).isEqualTo(51);
            assertThat(listed.movements()).hasSize(50);
            assertThat(listed.movements().getFirst().id()).isEqualTo(MovementId.of(InMemoryIdGenerator.nth(51)));
            assertThat(listed.hasMore()).isTrue();
        });
        assertThat(second).isInstanceOfSatisfying(Result.Listed.class, listed -> {
            assertThat(listed.movements()).singleElement()
                .satisfies(movement -> assertThat(movement.type()).isEqualTo(MovementType.INITIAL_LOAD));
            assertThat(listed.hasMore()).isFalse();
        });
    }

    @Test
    void givenAVariantWithoutStockLevel_whenListing_thenItIsUnknown() {
        assertThat(service.execute(new Query(ADMIN, VariantId.of(UUID.randomUUID()), 0)))
            .isEqualTo(new Result.UnknownVariant());
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenListing_thenItIsForbidden() {
        var customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(service.execute(new Query(customer, VARIANT, 0))).isEqualTo(new Result.Forbidden());
    }

    private void adjust(int quantity, String note) {
        assertThat(adjust.execute(new AdjustStock.Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()),
            quantity, Optional.ofNullable(note)))).isInstanceOf(AdjustStock.Result.Adjusted.class);
    }
}
