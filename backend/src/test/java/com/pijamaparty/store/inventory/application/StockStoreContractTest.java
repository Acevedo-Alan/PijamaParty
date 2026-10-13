package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.OrderRef;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadStockLevels}, {@link SaveStockLevels}, {@link StockHistory}, {@link AvailableStock},
 * {@link ReserveStock}, {@link OrderReservations} and {@link SettleReservations} over the same storage.
 */
public abstract class StockStoreContractTest<T extends LoadStockLevels & SaveStockLevels & StockHistory & AvailableStock
    & ReserveStock & OrderReservations & SettleReservations> extends ContractTest<T> {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant RESERVED = OPENED.plusSeconds(600);
    private static final Instant SETTLED = OPENED.plusSeconds(1200);
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    /** Creates the stock level of a new variant, with nothing on hand, as opening it does (UC-06). */
    protected abstract void givenStockLevel(VariantId variantId, Instant at);

    @Test
    void givenANewStockLevel_whenLocked_thenItHasNothingOnHandAndNoMovements() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenStockLevel(variant, OPENED);

        assertThat(subject.lockByVariantId(variant)).hasValueSatisfying(level -> {
            assertThat(level.variantId()).isEqualTo(variant);
            assertThat(level.onHand()).isZero();
            assertThat(level.hasMovements()).isFalse();
            assertThat(level.updatedAt()).isEqualTo(OPENED);
        });
        assertThat(subject.onHand(variant)).hasValue(0);
        assertThat(subject.movements(variant, 0, 10)).isEmpty();
    }

    @Test
    void givenNoStockLevel_whenLockedOrRead_thenNothingIsFound() {
        VariantId variant = VariantId.of(UUID.randomUUID());

        assertThat(subject.lockByVariantId(variant)).isEmpty();
        assertThat(subject.onHand(variant)).isEmpty();
        assertThat(subject.movements(variant, 0, 10)).isEmpty();
        assertThat(subject.findAdjustment(variant, AdjustmentKey.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenAnAdjustment_whenSaved_thenTheStockAndTheHistoryShowIt() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenStockLevel(variant, OPENED);
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());

        StockMovement movement = adjust(variant, 5, MovementNote.parse("Reposición"), key, OPENED.plusSeconds(60));

        assertThat(movement.type()).isEqualTo(MovementType.INITIAL_LOAD);
        assertThat(subject.lockByVariantId(variant)).hasValueSatisfying(level -> {
            assertThat(level.onHand()).isEqualTo(5);
            assertThat(level.hasMovements()).isTrue();
            assertThat(level.updatedAt()).isEqualTo(OPENED.plusSeconds(60));
        });
        assertThat(subject.onHand(variant)).hasValue(5);
        assertThat(subject.movements(variant, 0, 10)).containsExactly(movement);
        assertThat(subject.findAdjustment(variant, key)).contains(movement);
        assertThat(subject.findAdjustment(variant, AdjustmentKey.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenTheSameKeyOnAnotherVariant_whenLookingForTheAdjustment_thenItIsNotFound() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        VariantId other = VariantId.of(UUID.randomUUID());
        givenStockLevel(variant, OPENED);
        givenStockLevel(other, OPENED);
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());
        adjust(variant, 1, Optional.empty(), key, OPENED.plusSeconds(60));

        assertThat(subject.findAdjustment(other, key)).isEmpty();
        assertThat(adjust(other, 2, Optional.empty(), key, OPENED.plusSeconds(120)).quantity()).isEqualTo(2);
    }

    @Test
    void givenSeveralMovements_whenReadingTheHistory_thenTheNewestComeFirstInPages() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenStockLevel(variant, OPENED);
        StockMovement first = adjust(variant, 10, Optional.empty(), AdjustmentKey.of(UUID.randomUUID()),
            OPENED.plusSeconds(60));
        StockMovement second = adjust(variant, -3, MovementNote.parse("Falla"), AdjustmentKey.of(UUID.randomUUID()),
            OPENED.plusSeconds(120));
        StockMovement third = adjust(variant, 1, Optional.empty(), AdjustmentKey.of(UUID.randomUUID()),
            OPENED.plusSeconds(180));

        assertThat(subject.movements(variant, 0, 2)).containsExactly(third, second);
        assertThat(subject.movements(variant, 2, 2)).containsExactly(first);
        assertThat(second.type()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(subject.onHand(variant)).hasValue(8);
    }

    @Test
    void givenAStaleLevel_whenItsMovementWouldLeaveTheStockNegative_thenSavingFails() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenStockLevel(variant, OPENED);
        adjust(variant, 3, Optional.empty(), AdjustmentKey.of(UUID.randomUUID()), OPENED.plusSeconds(60));
        StockLevel stale = subject.lockByVariantId(variant).orElseThrow();
        adjust(variant, -3, Optional.empty(), AdjustmentKey.of(UUID.randomUUID()), OPENED.plusSeconds(120));

        stale.adjust(MovementId.of(UUID.randomUUID()), -3, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), OPENED.plusSeconds(180));

        assertThatThrownBy(() -> subject.save(stale)).isInstanceOf(RuntimeException.class);
        assertThat(subject.onHand(variant)).hasValue(0);
        assertThat(subject.movements(variant, 0, 10)).hasSize(2);
    }

    @Test
    void givenAStaleLevel_whenItsMovementReusesAnAdjustmentKey_thenSavingFails() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenStockLevel(variant, OPENED);
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());
        StockLevel stale = subject.lockByVariantId(variant).orElseThrow();
        adjust(variant, 3, Optional.empty(), key, OPENED.plusSeconds(60));

        stale.adjust(MovementId.of(UUID.randomUUID()), 3, Optional.empty(), ADMIN, key, OPENED.plusSeconds(120));

        assertThatThrownBy(() -> subject.save(stale)).isInstanceOf(RuntimeException.class);
        assertThat(subject.onHand(variant)).hasValue(3);
        assertThat(subject.movements(variant, 0, 10)).hasSize(1);
    }

    @Test
    void givenStockLevels_whenAskingForTheAvailableStock_thenOnlyVariantsWithALevelAreAnswered() {
        VariantId stocked = VariantId.of(UUID.randomUUID());
        VariantId empty = VariantId.of(UUID.randomUUID());
        VariantId unknown = VariantId.of(UUID.randomUUID());
        givenStockLevel(stocked, OPENED);
        givenStockLevel(empty, OPENED);
        adjust(stocked, 4, Optional.empty(), AdjustmentKey.of(UUID.randomUUID()), OPENED.plusSeconds(60));

        assertThat(subject.availableOf(List.of(stocked, empty, unknown, stocked)))
            .isEqualTo(Map.of(stocked, 4, empty, 0));
        assertThat(subject.availableOf(List.of())).isEmpty();
    }

    @Test
    void givenEnoughStock_whenAnOrderReservesIt_thenTheReservedUnitsAreNoLongerAvailable() {
        VariantId first = VariantId.of(UUID.randomUUID());
        VariantId second = VariantId.of(UUID.randomUUID());
        stocked(first, 3);
        stocked(second, 1);
        OrderRef order = OrderRef.of(UUID.randomUUID());
        StockMovement reservation = reservation(order, first, 2);

        assertThat(subject.reserveAll(List.of(reservation, reservation(order, second, 1))))
            .isEqualTo(new ReserveStock.Outcome.Reserved());
        persistAndForget();

        assertThat(subject.availableOf(List.of(first, second))).isEqualTo(Map.of(first, 1, second, 0));
        assertThat(subject.onHand(first)).hasValue(3);
        assertThat(subject.lockByVariantId(first)).hasValueSatisfying(level -> {
            assertThat(level.reserved()).isEqualTo(2);
            assertThat(level.updatedAt()).isEqualTo(RESERVED);
        });
        assertThat(subject.movements(first, 0, 1)).containsExactly(reservation);
        assertThat(subject.hasAny(order)).isTrue();
        assertThat(subject.outstanding(order)).hasSize(2).contains(reservation);
    }

    @Test
    void givenOneVariantShort_whenAnOrderReservesSeveral_thenNothingIsReservedAndTheShortVariantIsNamed() {
        VariantId enough = VariantId.of(UUID.randomUUID());
        VariantId scarce = VariantId.of(UUID.randomUUID());
        VariantId after = VariantId.of(UUID.randomUUID());
        stocked(enough, 5);
        stocked(scarce, 1);
        stocked(after, 5);
        OrderRef order = OrderRef.of(UUID.randomUUID());

        var outcome = subject.reserveAll(List.of(reservation(order, enough, 2), reservation(order, scarce, 2),
            reservation(order, after, 1)));
        persistAndForget();

        assertThat(outcome).isEqualTo(new ReserveStock.Outcome.Short(scarce, 1));
        assertThat(subject.availableOf(List.of(enough, scarce, after))).isEqualTo(Map.of(enough, 5, scarce, 1, after, 5));
        assertThat(subject.movements(enough, 0, 10)).hasSize(1);
        assertThat(subject.hasAny(order)).isFalse();
        assertThat(subject.outstanding(order)).isEmpty();
    }

    @Test
    void givenAVariantWithoutStockLevel_whenReserved_thenItIsShortWithNothingAvailable() {
        VariantId unknown = VariantId.of(UUID.randomUUID());

        assertThat(subject.reserveAll(List.of(reservation(OrderRef.of(UUID.randomUUID()), unknown, 1))))
            .isEqualTo(new ReserveStock.Outcome.Short(unknown, 0));
    }

    @Test
    void givenAReservedOrder_whenDeducted_thenTheUnitsLeaveTheStockOnceForGood() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        stocked(variant, 3);
        OrderRef order = OrderRef.of(UUID.randomUUID());
        StockMovement reservation = reserve(order, variant, 2);
        StockMovement deduction = reservation.deduction(MovementId.of(UUID.randomUUID()), SETTLED);

        subject.settle(List.of(deduction));
        persistAndForget();
        subject.settle(List.of(reservation.deduction(MovementId.of(UUID.randomUUID()), SETTLED)));
        subject.settle(List.of(reservation.release(MovementId.of(UUID.randomUUID()), SETTLED)));
        persistAndForget();

        assertThat(subject.onHand(variant)).hasValue(1);
        assertThat(subject.availableOf(List.of(variant))).isEqualTo(Map.of(variant, 1));
        assertThat(subject.lockByVariantId(variant)).hasValueSatisfying(level -> {
            assertThat(level.reserved()).isZero();
            assertThat(level.updatedAt()).isEqualTo(SETTLED);
        });
        assertThat(subject.movements(variant, 0, 10)).hasSize(3).startsWith(deduction);
        assertThat(subject.outstanding(order)).isEmpty();
        assertThat(subject.hasAny(order)).isTrue();
    }

    @Test
    void givenAReservedOrder_whenReleased_thenTheUnitsAreAvailableAgainOnce() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        stocked(variant, 3);
        OrderRef order = OrderRef.of(UUID.randomUUID());
        StockMovement reservation = reserve(order, variant, 2);
        StockMovement release = reservation.release(MovementId.of(UUID.randomUUID()), SETTLED);

        subject.settle(List.of(release));
        persistAndForget();
        subject.settle(List.of(reservation.release(MovementId.of(UUID.randomUUID()), SETTLED)));
        subject.settle(List.of(reservation.deduction(MovementId.of(UUID.randomUUID()), SETTLED)));
        persistAndForget();

        assertThat(subject.onHand(variant)).hasValue(3);
        assertThat(subject.availableOf(List.of(variant))).isEqualTo(Map.of(variant, 3));
        assertThat(subject.movements(variant, 0, 10)).hasSize(3).startsWith(release);
        assertThat(subject.outstanding(order)).isEmpty();
    }

    @Test
    void givenTwoOrders_whenOneIsReleased_thenTheOtherKeepsItsReservation() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        stocked(variant, 3);
        OrderRef released = OrderRef.of(UUID.randomUUID());
        OrderRef kept = OrderRef.of(UUID.randomUUID());
        StockMovement reservation = reserve(released, variant, 1);
        StockMovement keptReservation = reserve(kept, variant, 2);

        subject.settle(List.of(reservation.release(MovementId.of(UUID.randomUUID()), SETTLED)));
        persistAndForget();

        assertThat(subject.availableOf(List.of(variant))).isEqualTo(Map.of(variant, 1));
        assertThat(subject.outstanding(kept)).containsExactly(keptReservation);
        assertThat(subject.outstanding(released)).isEmpty();
    }

    @Test
    void givenReservedUnits_whenAStaleLevelTakesThemAway_thenSavingFails() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        stocked(variant, 3);
        StockLevel stale = subject.lockByVariantId(variant).orElseThrow();
        reserve(OrderRef.of(UUID.randomUUID()), variant, 2);

        stale.adjust(MovementId.of(UUID.randomUUID()), -2, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), OPENED.plusSeconds(180));

        assertThatThrownBy(() -> subject.save(stale)).isInstanceOf(RuntimeException.class);
        assertThat(subject.onHand(variant)).hasValue(3);
        assertThat(subject.availableOf(List.of(variant))).isEqualTo(Map.of(variant, 1));
    }

    private void stocked(VariantId variant, int onHand) {
        givenStockLevel(variant, OPENED);
        adjust(variant, onHand, Optional.empty(), AdjustmentKey.of(UUID.randomUUID()), OPENED.plusSeconds(60));
    }

    private static StockMovement reservation(OrderRef order, VariantId variant, int units) {
        return StockMovement.reservation(MovementId.of(UUID.randomUUID()), variant, order, units, RESERVED);
    }

    private StockMovement reserve(OrderRef order, VariantId variant, int units) {
        StockMovement reservation = reservation(order, variant, units);
        assertThat(subject.reserveAll(List.of(reservation))).isEqualTo(new ReserveStock.Outcome.Reserved());
        persistAndForget();
        return reservation;
    }

    /** Loads the level, adjusts it and saves it, as the use case does; returns the recorded movement. */
    private StockMovement adjust(VariantId variant, int quantity, Optional<MovementNote> note, AdjustmentKey key,
                                 Instant at) {
        StockLevel level = subject.lockByVariantId(variant).orElseThrow();
        var outcome = level.adjust(MovementId.of(UUID.randomUUID()), quantity, note, ADMIN, key, at);
        subject.save(level);
        persistAndForget();
        return ((StockLevel.AdjustOutcome.Adjusted) outcome).movement();
    }
}
