package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link PaymentNotifications} (UC-15, RNF-20.3): a notification is recorded once per provider, provider's
 * payment and verified status.
 */
public abstract class PaymentNotificationsContractTest<T extends PaymentNotifications> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");
    protected static final PaymentProvider MERCADOPAGO = PaymentProvider.MERCADOPAGO;

    protected static VerifiedPayment verified(String externalId, VerifiedStatus status) {
        return new VerifiedPayment(externalId, 1001, status, Money.of(2_100_050));
    }

    @Test
    void givenANewNotification_whenRecorded_thenItIsToBeProcessed() {
        assertThat(subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), NOW)).isTrue();
    }

    @Test
    void givenARecordedNotification_whenRecordedAgain_thenItWasAlreadyProcessed() {
        subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), NOW);
        persistAndForget();

        assertThat(subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), NOW.plusSeconds(900)))
            .isFalse();
    }

    @Test
    void givenAPendingPaymentRecorded_whenItsApprovalIsRecorded_thenItIsToBeProcessed() {
        subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.PENDING), NOW);
        persistAndForget();

        assertThat(subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), NOW.plusSeconds(60)))
            .isTrue();
    }

    @Test
    void givenARejectedAttemptRecorded_whenAnotherPaymentIsRecordedWithTheSameStatus_thenItIsToBeProcessed() {
        subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.REJECTED), NOW);
        persistAndForget();

        assertThat(subject.recordFirst(MERCADOPAGO, verified("222", VerifiedStatus.REJECTED), NOW.plusSeconds(60)))
            .isTrue();
    }
}
