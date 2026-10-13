package com.pijamaparty.store.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockLevelTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final VariantId VARIANT = VariantId.of(UUID.randomUUID());
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    @Test
    void givenAVariantWithoutMovements_whenStockIsAdded_thenTheMovementIsTheInitialLoad() {
        StockLevel level = StockLevel.of(VARIANT, 0, 0, false, OPENED);
        MovementId id = MovementId.of(UUID.randomUUID());
        AdjustmentKey key = AdjustmentKey.of(UUID.randomUUID());
        Optional<MovementNote> note = MovementNote.parse("Primera carga");

        var outcome = level.adjust(id, 5, note, ADMIN, key, NOW);

        var movement = new StockMovement(id, VARIANT, MovementType.INITIAL_LOAD, 5, note, Optional.of(ADMIN),
            Optional.of(key), Optional.empty(), NOW);
        assertThat(outcome).isEqualTo(new StockLevel.AdjustOutcome.Adjusted(movement, 5));
        assertThat(level.onHand()).isEqualTo(5);
        assertThat(level.hasMovements()).isTrue();
        assertThat(level.updatedAt()).isEqualTo(NOW);
        assertThat(level.newMovements()).containsExactly(movement);
    }

    @Test
    void givenAVariantWithMovements_whenAdjusted_thenTheMovementIsAnAdjustment() {
        StockLevel level = StockLevel.of(VARIANT, 5, 0, true, OPENED);

        var outcome = level.adjust(MovementId.of(UUID.randomUUID()), -2, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), NOW);

        assertThat(outcome).isInstanceOfSatisfying(StockLevel.AdjustOutcome.Adjusted.class, adjusted -> {
            assertThat(adjusted.movement().type()).isEqualTo(MovementType.ADJUSTMENT);
            assertThat(adjusted.onHand()).isEqualTo(3);
        });
    }

    @Test
    void givenAStockOf3_whenSubtracting5_thenItIsRejectedWithTheCurrentStock() {
        StockLevel level = StockLevel.of(VARIANT, 3, 0, true, OPENED);

        var outcome = level.adjust(MovementId.of(UUID.randomUUID()), -5, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), NOW);

        assertThat(outcome).isEqualTo(new StockLevel.AdjustOutcome.WouldGoNegative(3));
        assertThat(level.onHand()).isEqualTo(3);
        assertThat(level.updatedAt()).isEqualTo(OPENED);
        assertThat(level.newMovements()).isEmpty();
    }

    @Test
    void givenAStockNearTheMaximum_whenMoreIsAdded_thenItIsRejectedWithTheCurrentStock() {
        StockLevel level = StockLevel.of(VARIANT, StockLevel.MAX_ON_HAND - 1, 0, true, OPENED);

        var outcome = level.adjust(MovementId.of(UUID.randomUUID()), 2, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), NOW);

        assertThat(outcome).isEqualTo(new StockLevel.AdjustOutcome.WouldExceedMaximum(StockLevel.MAX_ON_HAND - 1));
        assertThat(level.newMovements()).isEmpty();
    }

    @Test
    void givenQuantities_whenChecked_thenOnlyNonZeroOnesUpTo10000EitherWayAreAdjustments() {
        assertThat(StockLevel.isValidAdjustment(1)).isTrue();
        assertThat(StockLevel.isValidAdjustment(-1)).isTrue();
        assertThat(StockLevel.isValidAdjustment(10_000)).isTrue();
        assertThat(StockLevel.isValidAdjustment(-10_000)).isTrue();
        assertThat(StockLevel.isValidAdjustment(0)).isFalse();
        assertThat(StockLevel.isValidAdjustment(10_001)).isFalse();
        assertThat(StockLevel.isValidAdjustment(-10_001)).isFalse();
        assertThat(StockLevel.isValidAdjustment(Integer.MIN_VALUE)).isFalse();
        assertThatThrownBy(() -> StockLevel.of(VARIANT, 0, 0, false, OPENED).adjust(MovementId.of(UUID.randomUUID()),
            0, Optional.empty(), ADMIN, AdjustmentKey.of(UUID.randomUUID()), NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAStockOf5With3Reserved_whenSubtracting3_thenItIsRejectedWithTheStockAndTheReservedUnits() {
        StockLevel level = StockLevel.of(VARIANT, 5, 3, true, OPENED);

        var outcome = level.adjust(MovementId.of(UUID.randomUUID()), -3, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), NOW);

        assertThat(outcome).isEqualTo(new StockLevel.AdjustOutcome.WouldGoBelowReserved(5, 3));
        assertThat(level.onHand()).isEqualTo(5);
        assertThat(level.newMovements()).isEmpty();
    }

    @Test
    void givenAStockOf5With3Reserved_whenSubtracting2_thenTheReservedUnitsStayCovered() {
        StockLevel level = StockLevel.of(VARIANT, 5, 3, true, OPENED);

        var outcome = level.adjust(MovementId.of(UUID.randomUUID()), -2, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), NOW);

        assertThat(outcome).isInstanceOfSatisfying(StockLevel.AdjustOutcome.Adjusted.class,
            adjusted -> assertThat(adjusted.onHand()).isEqualTo(3));
        assertThat(level.reserved()).isEqualTo(3);
    }

    @Test
    void givenAStockOf3With2Reserved_whenSubtracting5_thenItWouldGoNegative() {
        StockLevel level = StockLevel.of(VARIANT, 3, 2, true, OPENED);

        var outcome = level.adjust(MovementId.of(UUID.randomUUID()), -5, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), NOW);

        assertThat(outcome).isEqualTo(new StockLevel.AdjustOutcome.WouldGoNegative(3));
    }

    @Test
    void givenAStockOutOfRange_whenLoaded_thenItIsRefused() {
        assertThatThrownBy(() -> StockLevel.of(VARIANT, -1, 0, true, OPENED))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLevel.of(VARIANT, StockLevel.MAX_ON_HAND + 1, 0, true, OPENED))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLevel.of(VARIANT, 2, 3, true, OPENED))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLevel.of(VARIANT, 2, -1, true, OPENED))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
