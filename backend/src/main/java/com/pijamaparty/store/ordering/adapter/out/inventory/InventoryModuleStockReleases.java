package com.pijamaparty.store.ordering.adapter.out.inventory;

import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.ordering.application.StockReleases;
import com.pijamaparty.store.ordering.domain.OrderId;
import org.springframework.stereotype.Component;

/** Releases an order's stock through the public API of the inventory module, in the caller's transaction. */
@Component
class InventoryModuleStockReleases implements StockReleases {

    private final StockReservations reservations;

    InventoryModuleStockReleases(StockReservations reservations) {
        this.reservations = reservations;
    }

    @Override
    public void release(OrderId order) {
        reservations.release(order.value());
    }
}
