package com.pijamaparty.store.ordering.adapter.out.inventory;

import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.inventory.api.StockReservations.ReservationResult;
import com.pijamaparty.store.inventory.api.StockReservations.StockRequest;
import com.pijamaparty.store.ordering.application.ReserveOrderStock;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Reserves an order's stock through the public API of the inventory module, in the caller's transaction. */
@Component
class InventoryModuleOrderStock implements ReserveOrderStock {

    private final StockReservations reservations;

    InventoryModuleOrderStock(StockReservations reservations) {
        this.reservations = reservations;
    }

    @Override
    public Optional<Shortage> reserve(OrderId order, List<OrderLine> lines) {
        List<StockRequest> requests = lines.stream()
            .map(line -> new StockRequest(line.variantId(), line.quantity()))
            .toList();
        return switch (reservations.reserve(order.value(), requests)) {
            case ReservationResult.Reserved reserved -> Optional.empty();
            case ReservationResult.InsufficientStock insufficient ->
                Optional.of(new Shortage(insufficient.variantId(), insufficient.available()));
        };
    }
}
