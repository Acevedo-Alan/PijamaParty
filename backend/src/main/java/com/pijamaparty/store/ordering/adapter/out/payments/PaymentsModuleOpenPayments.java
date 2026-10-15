package com.pijamaparty.store.ordering.adapter.out.payments;

import com.pijamaparty.store.ordering.application.OpenPayments;
import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.payments.api.CheckoutOpening;
import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentCheckouts;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Opens the order's payment through the public API of the payments module, outside any transaction. The payment
 * table belongs to the system (RNF-27), so this is the one step that runs as {@code SYSTEM}: the use case already read
 * the order as the buyer who owns it.
 */
@Component
class PaymentsModuleOpenPayments implements OpenPayments {

    private final PaymentCheckouts checkouts;

    PaymentsModuleOpenPayments(PaymentCheckouts checkouts) {
        this.checkouts = checkouts;
    }

    @Override
    public Optional<URI> open(OrderToPay order) {
        var request = new CheckoutRequest(order.id().value(), order.number(), order.total(),
            PaymentProviderMapping.toPayments(order.provider()),
            new CheckoutRequest.Payer(order.buyerName(), order.buyerEmail()), order.reservationExpiresAt());
        return switch (CurrentActor.callAsSystem(() -> checkouts.open(request))) {
            case CheckoutOpening.Opened opened -> Optional.of(opened.checkoutUrl());
            case CheckoutOpening.ProviderUnavailable unavailable -> Optional.empty();
        };
    }
}
