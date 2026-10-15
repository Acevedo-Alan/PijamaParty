package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.ConfirmPayment.Result;
import com.pijamaparty.store.payments.domain.PaymentDecision;
import com.pijamaparty.store.payments.domain.PaymentReference;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-15: the transactional step of {@link ConfirmPaymentService}. A payment that refers to a checkout this store did
 * not create is unknown and recorded nowhere (T-E3b-03). A notification already recorded is not processed again
 * (T-E3-15, RNF-20.3); the payment is locked, so notifications of it run one after the other and the approval is
 * recorded and published once; the listeners of {@link com.pijamaparty.store.payments.api.PaymentApproved} run in
 * this same transaction.
 */
@Component
class VerifiedPaymentRecorder {

    private final PaymentNotifications notifications;
    private final PaymentsByCheckout paymentsByCheckout;
    private final PaymentsToConfirm paymentsToConfirm;
    private final PaymentConfirmations confirmations;
    private final PaymentEvents events;
    private final Clock clock;

    VerifiedPaymentRecorder(PaymentNotifications notifications, PaymentsByCheckout paymentsByCheckout,
                            PaymentsToConfirm paymentsToConfirm, PaymentConfirmations confirmations,
                            PaymentEvents events, Clock clock) {
        this.notifications = notifications;
        this.paymentsByCheckout = paymentsByCheckout;
        this.paymentsToConfirm = paymentsToConfirm;
        this.confirmations = confirmations;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result record(PaymentProvider provider, VerifiedPayment verified) {
        OptionalLong orderNumber = switch (verified.reference()) {
            case PaymentReference.ByOrderNumber byNumber -> OptionalLong.of(byNumber.orderNumber());
            case PaymentReference.ByCheckout byCheckout ->
                paymentsByCheckout.orderNumberOf(provider, byCheckout.checkoutId());
        };
        if (orderNumber.isEmpty()) {
            return new Result.UnknownPayment();
        }
        if (!notifications.recordFirst(provider, verified, orderNumber.getAsLong(), clock.instant())) {
            return new Result.AlreadyProcessed();
        }
        Optional<PaymentsToConfirm.PaymentToConfirm> found =
            paymentsToConfirm.lock(provider, orderNumber.getAsLong());
        if (found.isEmpty()) {
            return new Result.UnknownPayment();
        }
        var payment = found.get();
        long number = payment.orderNumber();
        return switch (PaymentDecision.decide(payment.status(), payment.amount(), verified)) {
            case PaymentDecision.Approve approve -> {
                record(payment, provider, PaymentStatus.APPROVED, verified);
                events.paymentApproved(payment.orderId(), provider);
                yield new Result.Approved(number);
            }
            case PaymentDecision.Reject reject -> {
                record(payment, provider, PaymentStatus.REJECTED, verified);
                yield new Result.Rejected(number);
            }
            case PaymentDecision.AlreadyApproved already -> new Result.AlreadyApproved(number);
            case PaymentDecision.Unchanged unchanged -> new Result.Unchanged(number);
            case PaymentDecision.AmountMismatch mismatch ->
                new Result.AmountMismatch(number, mismatch.expected(), mismatch.paid());
        };
    }

    private void record(PaymentsToConfirm.PaymentToConfirm payment, PaymentProvider provider, PaymentStatus status,
                        VerifiedPayment verified) {
        // The payment is locked and not approved, so the conditional update always applies.
        if (!confirmations.record(payment.orderId(), provider, status, verified.externalId(), clock.instant())) {
            throw new IllegalStateException("payment of order " + payment.orderNumber() + " changed while locked");
        }
    }
}
