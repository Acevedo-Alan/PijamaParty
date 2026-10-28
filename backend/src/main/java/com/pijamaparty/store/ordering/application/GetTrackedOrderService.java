package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderPayability;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * UC-14 (T-E3-08). The order is read in a short transaction of the port's own, then the photos of its lines from the
 * catalog in another.
 */
@Service
class GetTrackedOrderService implements GetTrackedOrder {

    private final TrackedOrders orders;
    private final LinePhotos linePhotos;
    private final Clock clock;

    GetTrackedOrderService(TrackedOrders orders, LinePhotos linePhotos, Clock clock) {
        this.orders = orders;
        this.linePhotos = linePhotos;
        this.clock = clock;
    }

    @Override
    public Result execute(Query query) {
        return orders.find(query.token())
            .<Result>map(order -> new Result.Found(order,
                OrderPayability.isPayable(order.status(), order.reservationExpiresAt(), clock.instant()),
                linePhotos.photosOf(order.lines().stream().map(OrderLine::variantId).distinct().toList())))
            .orElseGet(Result.NotFound::new);
    }
}
