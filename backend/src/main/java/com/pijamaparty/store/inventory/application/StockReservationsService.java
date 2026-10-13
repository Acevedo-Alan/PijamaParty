package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.OrderRef;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-16: reserves, deducts and releases the stock of an order. Variants are always taken in id order, so two orders
 * never wait on each other's stock levels in a cycle.
 */
@Service
class StockReservationsService implements StockReservations {

    private final ReserveStock reserveStock;
    private final OrderReservations orderReservations;
    private final SettleReservations settleReservations;
    private final IdGenerator ids;
    private final Clock clock;

    StockReservationsService(ReserveStock reserveStock, OrderReservations orderReservations,
                             SettleReservations settleReservations, IdGenerator ids, Clock clock) {
        this.reserveStock = reserveStock;
        this.orderReservations = orderReservations;
        this.settleReservations = settleReservations;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReservationResult reserve(UUID orderId, List<StockRequest> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("an order reserves at least one variant");
        }
        if (lines.stream().map(StockRequest::variantId).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("one line per variant");
        }
        OrderRef order = OrderRef.of(orderId);
        if (orderReservations.hasAny(order)) {
            return new ReservationResult.Reserved();
        }
        Instant now = clock.instant();
        List<StockMovement> reservations = lines.stream()
            .sorted(Comparator.comparing(StockRequest::variantId))
            .map(line -> StockMovement.reservation(MovementId.of(ids.newId()), VariantId.of(line.variantId()), order,
                line.quantity(), now))
            .toList();
        return switch (reserveStock.reserveAll(reservations)) {
            case ReserveStock.Outcome.Reserved reserved -> new ReservationResult.Reserved();
            case ReserveStock.Outcome.Short shortOf ->
                new ReservationResult.InsufficientStock(shortOf.variantId().value(), shortOf.available());
        };
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deduct(UUID orderId) {
        settle(orderId, StockMovement::deduction);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID orderId) {
        settle(orderId, StockMovement::release);
    }

    private void settle(UUID orderId, Settlement settlement) {
        List<StockMovement> outstanding = orderReservations.outstanding(OrderRef.of(orderId));
        if (outstanding.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        settleReservations.settle(outstanding.stream()
            .sorted(Comparator.comparing((StockMovement reservation) -> reservation.variantId().value()))
            .map(reservation -> settlement.of(reservation, MovementId.of(ids.newId()), now))
            .toList());
    }

    /** A deduction or a release of a reservation. */
    @FunctionalInterface
    private interface Settlement {

        StockMovement of(StockMovement reservation, MovementId id, Instant at);
    }
}
