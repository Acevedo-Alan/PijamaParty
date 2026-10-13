package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link PaymentVerifier} (UC-15, Liskov Substitution): every provider reports the payment it holds with
 * its order number, status and exact amount, tells an unknown payment apart, and turns its failures into
 * {@code Unavailable}.
 */
public abstract class PaymentVerifierContractTest<T extends PaymentVerifier> extends ContractTest<T> {

    protected static final String PAYMENT_ID = "1234567890";

    protected abstract PaymentProvider expectedProvider();

    /** The provider holds a payment with this id for the order, in this status, for this amount. */
    protected abstract void givenPayment(String id, long orderNumber, VerifiedStatus status, Money amount);

    /** Makes the provider unreachable or failing for the next calls. */
    protected abstract void providerIsDown();

    @Test
    void givenTheVerifier_whenAskedForItsProvider_thenItIsTheOneItTalksTo() {
        assertThat(subject.provider()).isEqualTo(expectedProvider());
    }

    @Test
    void givenAnApprovedPayment_whenVerified_thenItIsFoundWithItsOrderAndExactAmount() {
        givenPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));

        assertThat(subject.verify(PAYMENT_ID)).isEqualTo(new PaymentVerifier.Verification.Found(
            new VerifiedPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050))));
    }

    @Test
    void givenPaymentsInEveryStatus_whenVerified_thenEachKeepsItsStatus() {
        long number = 1001;
        for (VerifiedStatus status : VerifiedStatus.values()) {
            String id = Long.toString(5000 + number);
            givenPayment(id, number, status, Money.of(100));

            assertThat(subject.verify(id)).isEqualTo(new PaymentVerifier.Verification.Found(
                new VerifiedPayment(id, number, status, Money.of(100))));
            number++;
        }
    }

    @Test
    void givenAPaymentTheProviderDoesNotHold_whenVerified_thenItIsNotFound() {
        assertThat(subject.verify("999")).isInstanceOf(PaymentVerifier.Verification.NotFound.class);
    }

    @Test
    void givenTheProviderDown_whenAPaymentIsVerified_thenItIsUnavailableWithoutThrowing() {
        givenPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));
        providerIsDown();

        assertThat(subject.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }
}
