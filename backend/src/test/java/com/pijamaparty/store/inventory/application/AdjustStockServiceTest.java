package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.inventory.application.AdjustStock.Command;
import com.pijamaparty.store.inventory.application.AdjustStock.Result;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.OrderRef;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
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

class AdjustStockServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);
    private static final VariantId VARIANT = VariantId.of(UUID.randomUUID());

    private final InMemoryStockStore stock = new InMemoryStockStore();
    private final AdjustStockService service = new AdjustStockService(stock, stock, new InMemoryIdGenerator(),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void openVariant() {
        stock.open(VARIANT, OPENED);
    }

    @Test
    void givenAVariantWithoutMovements_whenStockIsAdded_thenTheMovementIsTheInitialLoad() {
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());

        Result result = service.execute(new Command(ADMIN, VARIANT, key, 5, Optional.of("  Primera carga ")));

        assertThat(result).isEqualTo(new Result.Adjusted(5));
        assertThat(stock.movements(VARIANT, 0, 10)).containsExactly(new StockMovement(
            MovementId.of(InMemoryIdGenerator.nth(1)), VARIANT, MovementType.INITIAL_LOAD, 5,
            MovementNote.parse("Primera carga"), Optional.of(ADMIN_ID), Optional.of(key), Optional.empty(), NOW));
    }

    @Test
    void givenAVariantWithStock_whenAdjusted_thenTheMovementIsAnAdjustmentAndTheStockChanges() {
        adjust(5);

        Result result = service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), -2,
            Optional.empty()));

        assertThat(result).isEqualTo(new Result.Adjusted(3));
        assertThat(stock.movements(VARIANT, 0, 1)).singleElement().satisfies(movement -> {
            assertThat(movement.type()).isEqualTo(MovementType.ADJUSTMENT);
            assertThat(movement.quantity()).isEqualTo(-2);
            assertThat(movement.note()).isEmpty();
        });
        assertThat(stock.onHand(VARIANT)).hasValue(3);
    }

    @Test
    void givenAStockOf3_whenSubtracting5_thenItIsRejected() {
        adjust(3);

        Result result = service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), -5,
            Optional.empty()));

        assertThat(result).isEqualTo(new Result.WouldGoNegative(3));
        assertThat(stock.onHand(VARIANT)).hasValue(3);
        assertThat(stock.movements(VARIANT, 0, 10)).hasSize(1);
    }

    @Test
    void givenAStockOf3With2ReservedByAnOrder_whenSubtracting2_thenItIsRejectedWithTheReservedUnits() {
        adjust(3);
        stock.reserveAll(List.of(StockMovement.reservation(MovementId.of(UUID.randomUUID()), VARIANT,
            OrderRef.of(UUID.randomUUID()), 2, NOW)));

        Result result = service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), -2,
            Optional.empty()));

        assertThat(result).isEqualTo(new Result.WouldGoBelowReserved(3, 2));
        assertThat(stock.onHand(VARIANT)).hasValue(3);
        assertThat(stock.reserved(VARIANT)).hasValue(2);
    }

    @Test
    void givenTheMaximumStock_whenMoreIsAdded_thenItIsRejected() {
        for (int i = 0; i < StockLevel.MAX_ON_HAND / StockLevel.MAX_ADJUSTMENT; i++) {
            adjust(StockLevel.MAX_ADJUSTMENT);
        }

        Result result = service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), 1,
            Optional.empty()));

        assertThat(result).isEqualTo(new Result.WouldExceedMaximum(StockLevel.MAX_ON_HAND));
    }

    @Test
    void givenAnAdjustmentWhoseResponseWasLost_whenRetriedWithTheSameKey_thenTheStockChangesOnlyOnce() {
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());
        service.execute(new Command(ADMIN, VARIANT, key, 5, Optional.of("Reposición")));

        Result retry = service.execute(new Command(ADMIN, VARIANT, key, 5, Optional.of(" Reposición ")));

        assertThat(retry).isEqualTo(new Result.AlreadyAdjusted(5));
        assertThat(stock.onHand(VARIANT)).hasValue(5);
        assertThat(stock.movements(VARIANT, 0, 10)).hasSize(1);
    }

    @Test
    void givenAUsedKey_whenAnotherAdjustmentUsesIt_thenItIsRejected() {
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());
        service.execute(new Command(ADMIN, VARIANT, key, 5, Optional.of("Reposición")));

        assertThat(service.execute(new Command(ADMIN, VARIANT, key, 4, Optional.of("Reposición"))))
            .isEqualTo(new Result.KeyReused());
        assertThat(service.execute(new Command(ADMIN, VARIANT, key, 5, Optional.empty())))
            .isEqualTo(new Result.KeyReused());
        assertThat(stock.movements(VARIANT, 0, 10)).hasSize(1);
    }

    @Test
    void givenAKeyUsedOnAnotherVariant_whenAdjusting_thenItIsANewAdjustment() {
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());
        VariantId other = VariantId.of(UUID.randomUUID());
        stock.open(other, OPENED);
        service.execute(new Command(ADMIN, VARIANT, key, 5, Optional.empty()));

        assertThat(service.execute(new Command(ADMIN, other, key, 5, Optional.empty())))
            .isEqualTo(new Result.Adjusted(5));
    }

    @Test
    void givenQuantitiesOutOfRange_whenAdjusting_thenTheyAreRejected() {
        for (int quantity : List.of(0, 10_001, -10_001, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            assertThat(service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), quantity,
                Optional.empty()))).isEqualTo(new Result.InvalidQuantity());
        }
        assertThat(service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), 10_000,
            Optional.empty()))).isEqualTo(new Result.Adjusted(10_000));
    }

    @Test
    void givenNotes_whenAdjusting_thenABlankOneIsNoNoteAndAnInvalidOneIsRejected() {
        assertThat(service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), 1,
            Optional.of("x".repeat(201))))).isEqualTo(new Result.InvalidNote());
        assertThat(service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), 1,
            Optional.of("dos\nlíneas")))).isEqualTo(new Result.InvalidNote());

        service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), 1, Optional.of("   ")));

        assertThat(stock.movements(VARIANT, 0, 10)).singleElement()
            .satisfies(movement -> assertThat(movement.note()).isEmpty());
    }

    @Test
    void givenAVariantWithoutStockLevel_whenAdjusting_thenItIsUnknown() {
        assertThat(service.execute(new Command(ADMIN, VariantId.of(UUID.randomUUID()),
            AdjustmentKey.of(UUID.randomUUID()), 1, Optional.empty()))).isEqualTo(new Result.UnknownVariant());
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenAdjusting_thenItIsForbidden() {
        var customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(service.execute(new Command(customer, VARIANT, AdjustmentKey.of(UUID.randomUUID()), 1,
            Optional.empty()))).isEqualTo(new Result.Forbidden());
        assertThat(service.execute(new Command(Actor.system(), VARIANT, AdjustmentKey.of(UUID.randomUUID()), 1,
            Optional.empty()))).isEqualTo(new Result.Forbidden());
        assertThat(stock.onHand(VARIANT)).hasValue(0);
    }

    private void adjust(int quantity) {
        assertThat(service.execute(new Command(ADMIN, VARIANT, AdjustmentKey.of(UUID.randomUUID()), quantity,
            Optional.empty()))).isInstanceOf(Result.Adjusted.class);
    }
}
