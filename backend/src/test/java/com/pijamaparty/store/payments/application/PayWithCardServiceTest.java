package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.CardPaymentRequest;
import com.pijamaparty.store.payments.api.CardPaymentResult;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class PayWithCardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T15:10:00Z");
    private static final Money TOTAL = Money.of(4_450_000);
    private static final long NUMBER = 1042;

    private final InMemoryPaymentStore payments = new InMemoryPaymentStore();
    private final InMemoryCardCharges cards = new InMemoryCardCharges();
    private final List<ConfirmPayment.Command> confirmed = new CopyOnWriteArrayList<>();
    private final ConfirmPayment confirmPayment = command -> {
        confirmed.add(command);
        return new ConfirmPayment.Result.Approved(NUMBER);
    };
    private final PayWithCardService service = new PayWithCardService(payments, cards, confirmPayment,
        UUID::randomUUID, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID order = UUID.randomUUID();

    private CardPaymentRequest request(UUID key) {
        return new CardPaymentRequest(order, NUMBER, TOTAL, "ana@example.com",
            new CardPaymentRequest.Card("card-token-1", "visa", Optional.empty(), 1, "DNI", "12345678"), key);
    }

    @Test
    void givenAnApprovedCard_whenPaid_thenThePendingPaymentWasStoredAndTheChargeIsConfirmedAsTheWebhookDoes() {
        CardPaymentResult result = service.pay(request(UUID.randomUUID()));

        assertThat(result).isEqualTo(new CardPaymentResult.Approved());
        assertThat(payments.paymentOf(order, PaymentProvider.MERCADOPAGO)).isPresent();
        assertThat(cards.charges()).singleElement().satisfies(charge -> {
            assertThat(charge.amount()).isEqualTo(TOTAL);
            assertThat(charge.payerEmail()).isEqualTo("ana@example.com");
        });
        assertThat(confirmed).singleElement()
            .isEqualTo(new ConfirmPayment.Command(PaymentProvider.MERCADOPAGO, "1000001"));
    }

    @Test
    void givenACardWithoutFunds_whenPaid_thenItIsRejectedForInsufficientFunds() {
        cards.rejectForFunds();

        assertThat(service.pay(request(UUID.randomUUID())))
            .isEqualTo(new CardPaymentResult.Rejected(CardRejection.INSUFFICIENT_FUNDS));
        assertThat(confirmed).hasSize(1);
    }

    @Test
    void givenACardInReview_whenPaid_thenItIsInProcess() {
        cards.review();

        assertThat(service.pay(request(UUID.randomUUID()))).isEqualTo(new CardPaymentResult.InProcess());
    }

    @Test
    void givenAUsedCardToken_whenPaid_thenTheBuyerIsAskedForTheCardAgainAndNothingIsConfirmed() {
        cards.refuse();

        assertThat(service.pay(request(UUID.randomUUID())))
            .isEqualTo(new CardPaymentResult.Rejected(CardRejection.BAD_CARD_DATA));
        assertThat(confirmed).isEmpty();
    }

    @Test
    void givenMercadoPagoDown_whenPaid_thenItIsUnavailableAndThePendingPaymentWaitsForARetry() {
        cards.down();

        assertThat(service.pay(request(UUID.randomUUID()))).isEqualTo(new CardPaymentResult.ProviderUnavailable());
        assertThat(payments.paymentOf(order, PaymentProvider.MERCADOPAGO)).isPresent();
        assertThat(confirmed).isEmpty();
    }

    @Test
    void givenTheSameSubmitTwice_whenPaid_thenOnePaymentIsCharged() {
        UUID key = UUID.randomUUID();

        service.pay(request(key));
        service.pay(request(key));

        assertThat(confirmed).extracting(ConfirmPayment.Command::externalPaymentId).containsOnly("1000001");
    }
}
