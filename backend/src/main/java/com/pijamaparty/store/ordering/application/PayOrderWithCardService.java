package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import com.pijamaparty.store.ordering.domain.OrderPayability;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * UC-14 (T-E3-29). Finds the order of the token, as {@link RetryOrderPayment} does, and charges the card only while
 * the order awaits payment, its reservation lasts and it pays with MercadoPago. Deliberately not transactional: each
 * read runs in a short transaction of its own, and no database connection is held while the provider answers (RNF-21).
 */
@Service
class PayOrderWithCardService implements PayOrderWithCard {

    private final TrackedOrders trackedOrders;
    private final OrdersToPay orders;
    private final ChargeCards cards;
    private final Clock clock;

    PayOrderWithCardService(TrackedOrders trackedOrders, OrdersToPay orders, ChargeCards cards, Clock clock) {
        this.trackedOrders = trackedOrders;
        this.orders = orders;
        this.cards = cards;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        Optional<TrackedOrder> tracked = trackedOrders.find(command.token());
        if (tracked.isEmpty()) {
            return new Result.NotFound();
        }
        Optional<OrderToPay> order = orders.find(tracked.get().id());
        if (order.isEmpty() || order.get().provider() != PaymentProvider.MERCADOPAGO
            || !OrderPayability.isPayable(order.get().status(), order.get().reservationExpiresAt(), clock.instant())) {
            return new Result.NotPayable();
        }
        return cards.charge(order.get(), command.card(), command.idempotencyKey());
    }
}
