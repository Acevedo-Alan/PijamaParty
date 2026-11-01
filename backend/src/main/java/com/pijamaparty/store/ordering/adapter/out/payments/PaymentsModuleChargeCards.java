package com.pijamaparty.store.ordering.adapter.out.payments;

import com.pijamaparty.store.ordering.application.ChargeCards;
import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.application.PayOrderWithCard;
import com.pijamaparty.store.payments.api.CardPaymentRequest;
import com.pijamaparty.store.payments.api.CardPaymentResult;
import com.pijamaparty.store.payments.api.CardPayments;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * T-E3-29: charges the order's card through the public API of the payments module, outside any transaction. As
 * {@link PaymentsModuleOpenPayments}, the one step that runs as {@code SYSTEM}: the payment table belongs to the system
 * (RNF-27), and the use case already read the order as the buyer who owns it.
 */
@Component
class PaymentsModuleChargeCards implements ChargeCards {

    private final CardPayments cardPayments;

    PaymentsModuleChargeCards(CardPayments cardPayments) {
        this.cardPayments = cardPayments;
    }

    @Override
    public PayOrderWithCard.Result charge(OrderToPay order, PayOrderWithCard.Card card, UUID idempotencyKey) {
        var request = new CardPaymentRequest(order.id().value(), order.number(), order.total(), order.buyerEmail(),
            new CardPaymentRequest.Card(card.token(), card.paymentMethodId(), card.issuerId(), card.installments(),
                card.identificationType(), card.identificationNumber()),
            idempotencyKey);
        return switch (CurrentActor.callAsSystem(() -> cardPayments.pay(request))) {
            case CardPaymentResult.Approved approved -> new PayOrderWithCard.Result.Approved();
            case CardPaymentResult.InProcess inProcess -> new PayOrderWithCard.Result.InProcess();
            case CardPaymentResult.Rejected rejected ->
                new PayOrderWithCard.Result.Rejected(PayOrderWithCard.Rejection.valueOf(rejected.reason().name()));
            case CardPaymentResult.ProviderUnavailable unavailable ->
                new PayOrderWithCard.Result.ProviderUnavailable();
        };
    }
}
