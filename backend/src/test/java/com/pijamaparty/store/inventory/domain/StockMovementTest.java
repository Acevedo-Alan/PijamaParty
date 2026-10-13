package com.pijamaparty.store.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockMovementTest {

    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final Optional<UserId> ADMIN = Optional.of(UserId.of(UUID.randomUUID()));
    private static final Optional<AdjustmentKey> KEY = Optional.of(AdjustmentKey.of(UUID.randomUUID()));

    @Test
    void givenATypedNote_whenParsed_thenItIsTrimmedAndUpTo200CharactersOnOneLine() {
        assertThat(MovementNote.parse("  Reposición de temporada ")).contains(new MovementNote("Reposición de temporada"));
        assertThat(MovementNote.parse("ñ".repeat(200))).isPresent();
        assertThat(MovementNote.parse("x".repeat(201))).isEmpty();
        assertThat(MovementNote.parse("   ")).isEmpty();
        assertThat(MovementNote.parse(null)).isEmpty();
        assertThat(MovementNote.parse("dos\nlíneas")).isEmpty();
        assertThat(MovementNote.parse("oculto​")).isEmpty();
        assertThatThrownBy(() -> new MovementNote(" sin recortar")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenANote_whenPrinted_thenItsTextIsHidden() {
        var note = new MovementNote("Devolución de Ana, DNI 12.345.678");

        assertThat(note.toString()).doesNotContain("Ana").doesNotContain("12.345.678");
        assertThat(movement(MovementType.ADJUSTMENT, 2, Optional.of(note), ADMIN, KEY).toString())
            .doesNotContain("Ana");
    }

    @Test
    void givenAMovement_whenComparedWithAnAdjustment_thenItIsARetryOnlyWithTheSameQuantityAndNote() {
        Optional<MovementNote> note = MovementNote.parse("Reposición");
        StockMovement movement = movement(MovementType.ADJUSTMENT, 2, note, ADMIN, KEY);

        assertThat(movement.repeats(2, MovementNote.parse("Reposición"))).isTrue();
        assertThat(movement.repeats(3, note)).isFalse();
        assertThat(movement.repeats(2, Optional.empty())).isFalse();
        assertThat(movement.repeats(2, MovementNote.parse("Otra"))).isFalse();
    }

    @Test
    void givenInconsistentValues_whenAMovementIsBuilt_thenItIsRefused() {
        assertThatThrownBy(() -> movement(MovementType.ADJUSTMENT, 0, Optional.empty(), ADMIN, KEY))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> movement(MovementType.INITIAL_LOAD, 1, Optional.empty(), Optional.empty(), KEY))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> movement(MovementType.ADJUSTMENT, 1, Optional.empty(), ADMIN, Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> movement(MovementType.RESERVATION, -1, Optional.empty(), Optional.empty(), KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAReservation_whenDeductedOrReleased_thenTheOrderTakesOrGivesBackItsUnits() {
        OrderRef order = OrderRef.of(UUID.randomUUID());
        VariantId variant = VariantId.of(UUID.randomUUID());
        StockMovement reservation = StockMovement.reservation(MovementId.of(UUID.randomUUID()), variant, order, 2, NOW);
        MovementId id = MovementId.of(UUID.randomUUID());
        Instant later = NOW.plusSeconds(60);

        assertThat(reservation.type()).isEqualTo(MovementType.RESERVATION);
        assertThat(reservation.quantity()).isEqualTo(-2);
        assertThat(reservation.units()).isEqualTo(2);
        assertThat(reservation.order()).contains(order);
        assertThat(reservation.deduction(id, later)).isEqualTo(new StockMovement(id, variant, MovementType.DEDUCTION,
            -2, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(order), later));
        assertThat(reservation.release(id, later)).isEqualTo(new StockMovement(id, variant, MovementType.RELEASE, 2,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(order), later));
        StockMovement release = reservation.release(id, later);
        assertThatThrownBy(() -> release.deduction(MovementId.of(UUID.randomUUID()), later))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenOrderMovementsWithoutTheirOrderOrSign_whenBuilt_thenTheyAreRefused() {
        Optional<OrderRef> order = Optional.of(OrderRef.of(UUID.randomUUID()));

        assertThatThrownBy(() -> orderMovement(MovementType.RESERVATION, -1, Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderMovement(MovementType.RESERVATION, 1, order))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderMovement(MovementType.DEDUCTION, 1, order))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderMovement(MovementType.RELEASE, -1, order))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMovement(MovementId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()),
            MovementType.ADJUSTMENT, 1, Optional.empty(), ADMIN, KEY, order, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(orderMovement(MovementType.RELEASE, 1, order).units()).isEqualTo(1);
    }

    private static StockMovement orderMovement(MovementType type, int quantity, Optional<OrderRef> order) {
        return new StockMovement(MovementId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), type, quantity,
            Optional.empty(), Optional.empty(), Optional.empty(), order, NOW);
    }

    private static StockMovement movement(MovementType type, int quantity, Optional<MovementNote> note,
                                          Optional<UserId> user, Optional<AdjustmentKey> key) {
        return new StockMovement(MovementId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), type, quantity,
            note, user, key, Optional.empty(), NOW);
    }
}
