package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * UC-14 (T-E3-08). Finds the order of the token and opens its payment as {@link OpenOrderPayment} does after the
 * checkout. Deliberately not transactional: no database connection is held while the provider answers (RNF-21).
 */
@Service
class RetryOrderPaymentService implements RetryOrderPayment {

    private final TrackedOrders orders;
    private final OpenOrderPayment openOrderPayment;

    RetryOrderPaymentService(TrackedOrders orders, OpenOrderPayment openOrderPayment) {
        this.orders = orders;
        this.openOrderPayment = openOrderPayment;
    }

    @Override
    public Result execute(Command command) {
        Optional<TrackedOrder> order = orders.find(command.token());
        if (order.isEmpty()) {
            return new Result.NotFound();
        }
        return switch (openOrderPayment.execute(new OpenOrderPayment.Command(order.get().id()))) {
            case OpenOrderPayment.Result.Opened opened -> new Result.Opened(opened.paymentUrl());
            case OpenOrderPayment.Result.ProviderUnavailable unavailable -> new Result.ProviderUnavailable();
            case OpenOrderPayment.Result.NotPayable notPayable -> new Result.NotPayable();
        };
    }
}
