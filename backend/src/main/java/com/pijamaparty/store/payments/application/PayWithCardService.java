package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CardPaymentRequest;
import com.pijamaparty.store.payments.api.CardPaymentResult;
import com.pijamaparty.store.payments.api.CardPayments;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * UC-14 (T-E3-29). Deliberately not transactional: the pending payment is stored first, in its own short transaction,
 * so the notification of the charge always finds it (UC-15); then the provider is called; then the charge's payment is
 * confirmed by {@link ConfirmPayment}, which asks the provider for it (R-06) and records it once (RNF-20.3), exactly
 * as the webhook does. The buyer is told what the provider answered the charge.
 */
@Service
class PayWithCardService implements CardPayments {

    private final SavePayments savePayments;
    private final CardCharges charges;
    private final ConfirmPayment confirmPayment;
    private final IdGenerator ids;
    private final Clock clock;

    PayWithCardService(SavePayments savePayments, CardCharges charges, ConfirmPayment confirmPayment, IdGenerator ids,
                       Clock clock) {
        this.savePayments = savePayments;
        this.charges = charges;
        this.confirmPayment = confirmPayment;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public CardPaymentResult pay(CardPaymentRequest request) {
        savePayments.insertPending(new SavePayments.NewPayment(ids.newId(), request.orderId(), request.orderNumber(),
            charges.provider(), request.amount(), clock.instant()));
        var charge = new CardCharges.CardCharge(request.orderNumber(), request.amount(), request.payerEmail(),
            request.card(), request.idempotencyKey());
        return switch (charges.charge(charge)) {
            case CardCharges.Charge.Unavailable unavailable -> new CardPaymentResult.ProviderUnavailable();
            case CardCharges.Charge.Refused refused -> new CardPaymentResult.Rejected(CardRejection.BAD_CARD_DATA);
            case CardCharges.Charge.Charged charged -> {
                // Whatever it answers, the webhook confirms the payment again later; the buyer hears the charge's status.
                confirmPayment.execute(new ConfirmPayment.Command(charges.provider(), charged.paymentId()));
                yield switch (charged.status()) {
                    case APPROVED -> new CardPaymentResult.Approved();
                    case IN_PROCESS -> new CardPaymentResult.InProcess();
                    case REJECTED -> new CardPaymentResult.Rejected(charged.rejection().orElse(CardRejection.OTHER));
                };
            }
        };
    }
}
