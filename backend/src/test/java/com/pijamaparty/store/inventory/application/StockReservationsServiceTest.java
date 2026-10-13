package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.inventory.api.StockReservations.ReservationResult;
import com.pijamaparty.store.inventory.api.StockReservations.StockRequest;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockReservationsServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-12T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    /** Ids chosen so the variant id order is LOW, then HIGH. */
    private static final VariantId LOW = VariantId.of(new UUID(1, 1));
    private static final VariantId HIGH = VariantId.of(new UUID(2, 2));

    private final InMemoryStockStore stock = new InMemoryStockStore();
    private final StockReservationsService service = new StockReservationsService(stock, stock, stock, stock, stock,
        new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID order = UUID.randomUUID();

    @Test
    void givenEnoughStock_whenAnOrderReserves_thenEveryLineIsReservedInVariantIdOrder() {
        stocked(LOW, 3);
        stocked(HIGH, 1);

        ReservationResult result = service.reserve(order,
            List.of(new StockRequest(HIGH.value(), 1), new StockRequest(LOW.value(), 2)));

        assertThat(result).isEqualTo(new ReservationResult.Reserved());
        assertThat(stock.availableOf(List.of(LOW, HIGH))).isEqualTo(Map.of(LOW, 1, HIGH, 0));
        assertThat(stock.movements(LOW, 0, 1)).singleElement().satisfies(movement -> {
            assertThat(movement.type()).isEqualTo(MovementType.RESERVATION);
            assertThat(movement.quantity()).isEqualTo(-2);
            assertThat(movement.order()).hasValueSatisfying(ref -> assertThat(ref.value()).isEqualTo(order));
            assertThat(movement.createdAt()).isEqualTo(NOW);
            // The ids follow the variant id order, not the order of the lines.
            assertThat(movement.id()).isEqualTo(MovementId.of(InMemoryIdGenerator.nth(1)));
        });
    }

    @Test
    void givenASkuWithStock1_whenAnOrderAsksFor2_thenItSeesNotEnoughStockNamingTheVariantAndNothingIsReserved() {
        stocked(LOW, 3);
        stocked(HIGH, 1);

        ReservationResult result = service.reserve(order,
            List.of(new StockRequest(LOW.value(), 2), new StockRequest(HIGH.value(), 2)));

        assertThat(result).isEqualTo(new ReservationResult.InsufficientStock(HIGH.value(), 1));
        assertThat(stock.availableOf(List.of(LOW, HIGH))).isEqualTo(Map.of(LOW, 3, HIGH, 1));
    }

    @Test
    void givenAnOrderThatAlreadyReserved_whenItReservesAgain_thenNothingIsReservedTwice() {
        stocked(LOW, 3);
        service.reserve(order, List.of(new StockRequest(LOW.value(), 2)));

        ReservationResult again = service.reserve(order, List.of(new StockRequest(LOW.value(), 2)));

        assertThat(again).isEqualTo(new ReservationResult.Reserved());
        assertThat(stock.availableOf(List.of(LOW))).isEqualTo(Map.of(LOW, 1));
    }

    @Test
    void givenAReservedOrder_whenAnApprovedPaymentDeductsItTwice_thenTheStockIsDeductedOnce() {
        stocked(LOW, 3);
        stocked(HIGH, 1);
        service.reserve(order, List.of(new StockRequest(LOW.value(), 2), new StockRequest(HIGH.value(), 1)));

        service.deduct(order);
        service.deduct(order);

        assertThat(stock.onHand(LOW)).hasValue(1);
        assertThat(stock.onHand(HIGH)).hasValue(0);
        assertThat(stock.reserved(LOW)).hasValue(0);
        assertThat(stock.availableOf(List.of(LOW, HIGH))).isEqualTo(Map.of(LOW, 1, HIGH, 0));
        assertThat(stock.movements(LOW, 0, 1)).singleElement()
            .satisfies(movement -> assertThat(movement.type()).isEqualTo(MovementType.DEDUCTION));
    }

    @Test
    void givenAReservedOrder_whenReleasedTwice_thenTheStockIsAvailableAgainOnce() {
        stocked(LOW, 3);
        service.reserve(order, List.of(new StockRequest(LOW.value(), 2)));

        service.release(order);
        service.release(order);
        service.deduct(order);

        assertThat(stock.onHand(LOW)).hasValue(3);
        assertThat(stock.availableOf(List.of(LOW))).isEqualTo(Map.of(LOW, 3));
        assertThat(stock.movements(LOW, 0, 10)).extracting(StockMovement::type)
            .containsExactly(MovementType.RELEASE, MovementType.RESERVATION, MovementType.INITIAL_LOAD);
    }

    @Test
    void givenAPaidOrder_whenRestockedTwice_thenItsUnitsAreBackOnHandOnce() {
        stocked(LOW, 3);
        stocked(HIGH, 1);
        service.reserve(order, List.of(new StockRequest(LOW.value(), 2), new StockRequest(HIGH.value(), 1)));
        service.deduct(order);

        service.restock(order);
        service.restock(order);

        assertThat(stock.onHand(LOW)).hasValue(3);
        assertThat(stock.availableOf(List.of(LOW, HIGH))).isEqualTo(Map.of(LOW, 3, HIGH, 1));
        assertThat(stock.movements(HIGH, 0, 1)).singleElement().satisfies(movement -> {
            assertThat(movement.type()).isEqualTo(MovementType.RESTOCK);
            assertThat(movement.quantity()).isEqualTo(1);
            assertThat(movement.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    void givenAnOrderNeverDeducted_whenRestocked_thenNothingChanges() {
        stocked(LOW, 3);
        service.reserve(order, List.of(new StockRequest(LOW.value(), 2)));
        service.release(order);

        service.restock(order);

        assertThat(stock.onHand(LOW)).hasValue(3);
        assertThat(stock.movements(LOW, 0, 10)).extracting(StockMovement::type)
            .containsExactly(MovementType.RELEASE, MovementType.RESERVATION, MovementType.INITIAL_LOAD);
    }

    @Test
    void givenAnOrderWithoutReservations_whenDeductedOrReleased_thenNothingChanges() {
        stocked(LOW, 3);

        service.deduct(order);
        service.release(order);

        assertThat(stock.movements(LOW, 0, 10)).hasSize(1);
        assertThat(stock.availableOf(List.of(LOW))).isEqualTo(Map.of(LOW, 3));
    }

    @Test
    void givenNoLinesOrAVariantTwice_whenReserving_thenItIsRefused() {
        assertThatThrownBy(() -> service.reserve(order, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reserve(order,
            List.of(new StockRequest(LOW.value(), 1), new StockRequest(LOW.value(), 2))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockRequest(LOW.value(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockRequest(LOW.value(), StockRequest.MAX_QUANTITY + 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private void stocked(VariantId variant, int onHand) {
        stock.open(variant, OPENED);
        StockLevel level = stock.lockByVariantId(variant).orElseThrow();
        level.adjust(MovementId.of(UUID.randomUUID()), onHand, Optional.empty(), ADMIN,
            AdjustmentKey.of(UUID.randomUUID()), OPENED);
        stock.save(level);
    }
}
