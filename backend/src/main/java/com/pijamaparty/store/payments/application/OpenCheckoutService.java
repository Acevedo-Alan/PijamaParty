package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CheckoutOpening;
import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentCheckouts;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * UC-14: opens the payment of an order. Deliberately not transactional: the payment and its attempt are committed
 * before the provider is called and the checkout is recorded after it answers, each in its own short transaction
 * (RNF-21). A checkout that expired or is about to (a MODO payment request, T-E3b-02) gets a new attempt.
 */
@Service
class OpenCheckoutService implements PaymentCheckouts {

    private final LoadPayments loadPayments;
    private final SavePayments savePayments;
    private final Map<PaymentProvider, PaymentGateway> gateways;
    private final IdGenerator ids;
    private final Clock clock;

    OpenCheckoutService(LoadPayments loadPayments, SavePayments savePayments, List<PaymentGateway> gateways,
                        IdGenerator ids, Clock clock) {
        this.loadPayments = loadPayments;
        this.savePayments = savePayments;
        this.gateways = new EnumMap<>(PaymentProvider.class);
        for (PaymentGateway gateway : gateways) {
            if (this.gateways.put(gateway.provider(), gateway) != null) {
                throw new IllegalStateException("two payment gateways for " + gateway.provider());
            }
        }
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public CheckoutOpening open(CheckoutRequest request) {
        PaymentGateway gateway = gateways.get(request.provider());
        if (gateway == null) {
            throw new IllegalStateException("no payment gateway for " + request.provider());
        }
        Instant now = clock.instant();
        Optional<Checkout> current = loadPayments.checkoutOf(request.orderId(), request.provider());
        if (current.isPresent() && current.get().usableAt(now)) {
            return new CheckoutOpening.Opened(current.get().url());
        }
        savePayments.insertPending(new SavePayments.NewPayment(ids.newId(), request.orderId(), request.orderNumber(),
            request.provider(), request.amount(), now));
        // RNF-20.8: recorded before the call, so an answer that never arrives leaves a trace of the attempt.
        CheckoutAttempt attempt = savePayments.startAttempt(request.orderId(), request.provider(), now)
            .orElseThrow(() -> new IllegalStateException("the payment of order " + request.orderNumber()
                + " was not stored"));
        return switch (gateway.createCheckout(request, attempt)) {
            case PaymentGateway.Outcome.Unavailable unavailable -> new CheckoutOpening.ProviderUnavailable();
            case PaymentGateway.Outcome.Created created -> {
                if (savePayments.recordCheckout(request.orderId(), request.provider(), attempt.number(),
                    created.checkout(), clock.instant())) {
                    yield new CheckoutOpening.Opened(created.checkout().url());
                }
                // A concurrent call recorded the checkout first, or a later attempt has one: that is the current one.
                yield loadPayments.checkoutOf(request.orderId(), request.provider())
                    .<CheckoutOpening>map(checkout -> new CheckoutOpening.Opened(checkout.url()))
                    .orElseGet(CheckoutOpening.ProviderUnavailable::new);
            }
        };
    }
}
