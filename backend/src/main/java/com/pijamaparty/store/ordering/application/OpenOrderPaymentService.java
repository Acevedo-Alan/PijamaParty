package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.domain.OrderPayability;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * UC-14. Deliberately not transactional: the order is read in a short transaction of its own, and no database
 * connection is held while the provider answers (RNF-21).
 */
@Service
class OpenOrderPaymentService implements OpenOrderPayment {

    private final OrdersToPay orders;
    private final OpenPayments payments;
    private final Clock clock;

    OpenOrderPaymentService(OrdersToPay orders, OpenPayments payments, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        Optional<OrderToPay> order = orders.find(command.orderId());
        if (order.isEmpty()
            || !OrderPayability.isPayable(order.get().status(), order.get().reservationExpiresAt(), clock.instant())) {
            return new Result.NotPayable();
        }
        return switch (payments.open(order.get())) {
            case OpenPayments.Opening.Redirect redirect -> new Result.Opened(redirect.paymentUrl());
            case OpenPayments.Opening.OnSite onSite -> new Result.OnSite();
            case OpenPayments.Opening.Unavailable unavailable -> new Result.ProviderUnavailable();
        };
    }
}
