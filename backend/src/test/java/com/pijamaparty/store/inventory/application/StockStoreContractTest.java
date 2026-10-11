package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadStockLevels}, {@link SaveStockLevels} and {@link StockHistory} over the same storage. */
public abstract class StockStoreContractTest<T extends LoadStockLevels & SaveStockLevels & StockHistory>
    extends ContractTest<T> {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
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
