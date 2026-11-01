package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Contract of {@link ChargeCards} (UC-14, T-E3-29): what the buyer is told of a card charged on the page. */
public abstract class ChargeCardsContractTest<T extends ChargeCards> extends ContractTest<T> {

    private static final AtomicLong NUMBERS = new AtomicLong(8000);

    protected static final PayOrderWithCard.Card CARD =
        new PayOrderWithCard.Card("card-token-1", "visa", Optional.empty(), 1, "DNI", "12345678");

    /** The next cards are rejected for insufficient funds. */
    protected abstract void cardsAreRejectedForInsufficientFunds();

    /** The next cards stay in review. */
    protected abstract void cardsAreInReview();

    /** The provider fails or cannot be reached. */
    protected abstract void providerIsDown();

    protected OrderToPay orderToPay() {
        return new OrderToPay(OrderId.of(UUID.randomUUID()), NUMBERS.incrementAndGet(), OrderStatus.AWAITING_PAYMENT,
            Money.of(4_450_000), PaymentProvider.MERCADOPAGO, "Ana Pérez", "ana@example.com",
            Instant.now().plusSeconds(1800));
    }

    @Test
    void givenAnApprovedCard_whenCharged_thenItIsApproved() {
        assertThat(subject.charge(orderToPay(), CARD, UUID.randomUUID()))
            .isEqualTo(new PayOrderWithCard.Result.Approved());
    }

    @Test
    void givenACardWithoutFunds_whenCharged_thenItIsRejectedForInsufficientFunds() {
        cardsAreRejectedForInsufficientFunds();

        assertThat(subject.charge(orderToPay(), CARD, UUID.randomUUID()))
            .isEqualTo(new PayOrderWithCard.Result.Rejected(PayOrderWithCard.Rejection.INSUFFICIENT_FUNDS));
    }

    @Test
    void givenACardInReview_whenCharged_thenItIsInProcess() {
        cardsAreInReview();

        assertThat(subject.charge(orderToPay(), CARD, UUID.randomUUID()))
            .isEqualTo(new PayOrderWithCard.Result.InProcess());
    }

    @Test
    void givenTheProviderDown_whenCharged_thenItIsUnavailable() {
        providerIsDown();

        assertThat(subject.charge(orderToPay(), CARD, UUID.randomUUID()))
            .isEqualTo(new PayOrderWithCard.Result.ProviderUnavailable());
    }
}
