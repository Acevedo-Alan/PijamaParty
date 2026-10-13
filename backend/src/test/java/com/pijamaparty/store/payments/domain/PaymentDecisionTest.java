package com.pijamaparty.store.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.Money;
import org.junit.jupiter.api.Test;

class PaymentDecisionTest {

    private static final Money TOTAL = Money.of(2_100_050);

    @Test
    void givenAPendingPayment_whenApprovedForTheOrdersTotal_thenItIsApproved() {
        assertThat(PaymentDecision.decide(PaymentStatus.PENDING, TOTAL, verified(VerifiedStatus.APPROVED, TOTAL)))
            .isEqualTo(new PaymentDecision.Approve());
    }

    @Test
    void givenARejectedAttempt_whenTheNextOneIsApproved_thenItIsApproved() {
        assertThat(PaymentDecision.decide(PaymentStatus.REJECTED, TOTAL, verified(VerifiedStatus.APPROVED, TOTAL)))
            .isEqualTo(new PaymentDecision.Approve());
    }

    @Test
    void givenAPendingPayment_whenApprovedForAnotherAmount_thenNothingIsApproved() {
        Money less = Money.of(2_100_000);

        assertThat(PaymentDecision.decide(PaymentStatus.PENDING, TOTAL, verified(VerifiedStatus.APPROVED, less)))
            .isEqualTo(new PaymentDecision.AmountMismatch(TOTAL, less));
    }

    @Test
    void givenAnApprovedPayment_whenApprovedAgain_thenItWasAlreadyApproved() {
        assertThat(PaymentDecision.decide(PaymentStatus.APPROVED, TOTAL, verified(VerifiedStatus.APPROVED, TOTAL)))
            .isEqualTo(new PaymentDecision.AlreadyApproved());
    }

    @Test
    void givenAnApprovedPayment_whenALaterAttemptIsRejectedOrRefunded_thenItStaysApproved() {
        for (VerifiedStatus status : new VerifiedStatus[] {VerifiedStatus.REJECTED, VerifiedStatus.PENDING,
            VerifiedStatus.OTHER}) {
            assertThat(PaymentDecision.decide(PaymentStatus.APPROVED, TOTAL, verified(status, TOTAL))).as(status.name())
                .isEqualTo(new PaymentDecision.Unchanged());
        }
    }

    @Test
    void givenAPendingOrRejectedPayment_whenRejected_thenTheRejectionIsRecorded() {
        assertThat(PaymentDecision.decide(PaymentStatus.PENDING, TOTAL, verified(VerifiedStatus.REJECTED, TOTAL)))
            .isEqualTo(new PaymentDecision.Reject());
        assertThat(PaymentDecision.decide(PaymentStatus.REJECTED, TOTAL, verified(VerifiedStatus.REJECTED, TOTAL)))
            .isEqualTo(new PaymentDecision.Reject());
    }

    @Test
    void givenAPendingPayment_whenStillPendingOrSomethingElse_thenNothingChanges() {
        assertThat(PaymentDecision.decide(PaymentStatus.PENDING, TOTAL, verified(VerifiedStatus.PENDING, TOTAL)))
            .isEqualTo(new PaymentDecision.Unchanged());
        assertThat(PaymentDecision.decide(PaymentStatus.PENDING, TOTAL, verified(VerifiedStatus.OTHER, TOTAL)))
            .isEqualTo(new PaymentDecision.Unchanged());
    }

    @Test
    void givenAProvidersPaymentWithoutAnIdOrOrder_whenBuilt_thenItIsRefused() {
        assertThatThrownBy(() -> new VerifiedPayment(" ", 1001, VerifiedStatus.APPROVED, TOTAL))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerifiedPayment("x".repeat(201), 1001, VerifiedStatus.APPROVED, TOTAL))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerifiedPayment("123", 0, VerifiedStatus.APPROVED, TOTAL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static VerifiedPayment verified(VerifiedStatus status, Money amount) {
        return new VerifiedPayment("1234567890", 1001, status, amount);
    }
}
