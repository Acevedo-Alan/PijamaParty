package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.CardPaymentRequest;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.CardCharges.Charge;
import com.pijamaparty.store.payments.application.CardCharges.ChargeStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Contract of {@link CardCharges} (UC-14, T-E3-29): what every provider charging cards on the page must do. */
public abstract class CardChargesContractTest<T extends CardCharges> extends ContractTest<T> {

    private static final AtomicLong NUMBERS = new AtomicLong(7000);

    protected abstract PaymentProvider expectedProvider();

    /** The next cards are rejected for insufficient funds. */
    protected abstract void cardsAreRejectedForInsufficientFunds();

    /** The next cards stay in review at the provider. */
    protected abstract void cardsAreInReview();

    /** The provider refuses the next requests themselves, as for a used card token. */
    protected abstract void tokensAreRefused();

    /** The provider fails or cannot be reached. */
    protected abstract void providerIsDown();

    protected static CardCharges.CardCharge charge(long orderNumber, UUID key) {
        return new CardCharges.CardCharge(orderNumber, Money.of(4_450_000), "ana@example.com",
            new CardPaymentRequest.Card("card-token-1", "visa", Optional.of("310"), 3, "DNI", "12345678"), key);
    }

    @Test
    void givenTheProvider_whenAsked_thenItIsTheExpectedOne() {
        assertThat(subject.provider()).isEqualTo(expectedProvider());
    }

    @Test
    void givenAnApprovedCard_whenCharged_thenThePaymentIsApprovedWithItsId() {
        Charge result = subject.charge(charge(NUMBERS.incrementAndGet(), UUID.randomUUID()));

        assertThat(result).isInstanceOfSatisfying(Charge.Charged.class, charged -> {
            assertThat(charged.paymentId()).isNotBlank();
            assertThat(charged.status()).isEqualTo(ChargeStatus.APPROVED);
            assertThat(charged.rejection()).isEmpty();
        });
    }

    @Test
    void givenTheSameSubmitTwice_whenCharged_thenTheSamePaymentIsAnswered() {
        var submit = charge(NUMBERS.incrementAndGet(), UUID.randomUUID());

        Charge first = subject.charge(submit);
        Charge second = subject.charge(submit);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void givenTwoSubmitsOfTheSameOrder_whenCharged_thenEachIsItsOwnPayment() {
        long number = NUMBERS.incrementAndGet();

        Charge first = subject.charge(charge(number, UUID.randomUUID()));
        Charge second = subject.charge(charge(number, UUID.randomUUID()));

        assertThat(((Charge.Charged) second).paymentId()).isNotEqualTo(((Charge.Charged) first).paymentId());
    }

    @Test
    void givenACardWithoutFunds_whenCharged_thenItIsRejectedForInsufficientFunds() {
        cardsAreRejectedForInsufficientFunds();

        Charge result = subject.charge(charge(NUMBERS.incrementAndGet(), UUID.randomUUID()));

        assertThat(result).isInstanceOfSatisfying(Charge.Charged.class, charged -> {
            assertThat(charged.status()).isEqualTo(ChargeStatus.REJECTED);
            assertThat(charged.rejection()).contains(CardRejection.INSUFFICIENT_FUNDS);
        });
    }

    @Test
    void givenACardInReview_whenCharged_thenItIsInProcess() {
        cardsAreInReview();

        Charge result = subject.charge(charge(NUMBERS.incrementAndGet(), UUID.randomUUID()));

        assertThat(result).isInstanceOfSatisfying(Charge.Charged.class,
            charged -> assertThat(charged.status()).isEqualTo(ChargeStatus.IN_PROCESS));
    }

    @Test
    void givenAUsedToken_whenCharged_thenTheChargeIsRefused() {
        tokensAreRefused();

        assertThat(subject.charge(charge(NUMBERS.incrementAndGet(), UUID.randomUUID())))
            .isInstanceOf(Charge.Refused.class);
    }

    @Test
    void givenTheProviderDown_whenCharged_thenItIsUnavailable() {
        providerIsDown();

        assertThat(subject.charge(charge(NUMBERS.incrementAndGet(), UUID.randomUUID())))
            .isInstanceOf(Charge.Unavailable.class);
    }
}
