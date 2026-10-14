package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.payments.api.PaymentApproved;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.ConfirmPayment.Command;
import com.pijamaparty.store.payments.application.ConfirmPayment.Result;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:10:00Z");
    private static final PaymentProvider MERCADOPAGO = PaymentProvider.MERCADOPAGO;
    private static final Money TOTAL = Money.of(2_100_050);
    private static final long NUMBER = 1001;

    private final InMemoryPaymentStore payments = new InMemoryPaymentStore();
    private final InMemoryPaymentVerifier mercadoPago = new InMemoryPaymentVerifier(MERCADOPAGO);
    private final InMemoryPaymentEvents events = new InMemoryPaymentEvents();
    private final InMemoryPaymentNotifications notifications = new InMemoryPaymentNotifications();
    private final ConfirmPaymentService service = new ConfirmPaymentService(List.of(mercadoPago),
        new VerifiedPaymentRecorder(notifications, payments, payments, events, Clock.fixed(NOW, ZoneOffset.UTC)));
    private final UUID order = UUID.randomUUID();

    @BeforeEach
    void placeTheOrdersPayment() {
        payments.insertPending(new SavePayments.NewPayment(UUID.randomUUID(), order, NUMBER, MERCADOPAGO, TOTAL,
            NOW.minusSeconds(600)));
    }

    @Test
    void givenAnApprovedPayment_whenNotified_thenThePaymentIsApprovedAndTheOrderIsTold() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.APPROVED, TOTAL));

        Result result = service.execute(new Command(MERCADOPAGO, "111"));

        assertThat(result).isEqualTo(new Result.Approved(NUMBER));
        assertThat(statusOf()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payments.paymentOf(order, MERCADOPAGO).orElseThrow().externalPaymentId()).contains("111");
        assertThat(events.published()).containsExactly(new PaymentApproved(order, MERCADOPAGO));
    }

    @Test
    void givenTheSameApprovalNotifiedTwice_whenTheSecondIsProcessed_thenTheOrderIsToldOnce() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.APPROVED, TOTAL));
        service.execute(new Command(MERCADOPAGO, "111"));

        Result again = service.execute(new Command(MERCADOPAGO, "111"));

        assertThat(again).isEqualTo(new Result.AlreadyProcessed());
        assertThat(events.published()).hasSize(1);
        assertThat(notifications.recorded()).containsExactly(new InMemoryPaymentNotifications.Recorded(MERCADOPAGO,
            "111", VerifiedStatus.APPROVED, NUMBER));
    }

    @Test
    void givenAnApprovedPayment_whenAnotherApprovedPaymentOfTheOrderIsNotified_thenTheOrderIsNotToldAgain() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.APPROVED, TOTAL));
        mercadoPago.holds(new VerifiedPayment("222", NUMBER, VerifiedStatus.APPROVED, TOTAL));
        service.execute(new Command(MERCADOPAGO, "111"));

        Result result = service.execute(new Command(MERCADOPAGO, "222"));

        assertThat(result).isEqualTo(new Result.AlreadyApproved(NUMBER));
        assertThat(events.published()).hasSize(1);
        assertThat(payments.paymentOf(order, MERCADOPAGO).orElseThrow().externalPaymentId()).contains("111");
    }

    @Test
    void givenTheSameRejectionNotifiedTwice_whenTheSecondIsProcessed_thenNothingHappensAgain() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.REJECTED, TOTAL));
        service.execute(new Command(MERCADOPAGO, "111"));

        assertThat(service.execute(new Command(MERCADOPAGO, "111"))).isEqualTo(new Result.AlreadyProcessed());
        assertThat(statusOf()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void givenAPendingPaymentNotified_whenItIsApprovedAndNotifiedAgain_thenTheOrderIsPaid() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.PENDING, TOTAL));
        service.execute(new Command(MERCADOPAGO, "111"));
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.APPROVED, TOTAL));

        Result result = service.execute(new Command(MERCADOPAGO, "111"));

        assertThat(result).isEqualTo(new Result.Approved(NUMBER));
        assertThat(events.published()).containsExactly(new PaymentApproved(order, MERCADOPAGO));
        assertThat(notifications.recorded()).extracting(InMemoryPaymentNotifications.Recorded::status)
            .containsExactlyInAnyOrder(VerifiedStatus.PENDING, VerifiedStatus.APPROVED);
    }

    @Test
    void givenARejectedPayment_whenNotified_thenTheRejectionIsRecordedAndTheOrderIsNotTold() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.REJECTED, TOTAL));

        Result result = service.execute(new Command(MERCADOPAGO, "111"));

        assertThat(result).isEqualTo(new Result.Rejected(NUMBER));
        assertThat(statusOf()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenARejectedAttemptAndThenAnApprovedOne_whenBothAreNotified_thenTheOrderIsPaid() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.REJECTED, TOTAL));
        mercadoPago.holds(new VerifiedPayment("222", NUMBER, VerifiedStatus.APPROVED, TOTAL));

        service.execute(new Command(MERCADOPAGO, "111"));
        Result result = service.execute(new Command(MERCADOPAGO, "222"));

        assertThat(result).isEqualTo(new Result.Approved(NUMBER));
        assertThat(payments.paymentOf(order, MERCADOPAGO).orElseThrow().externalPaymentId()).contains("222");
    }

    @Test
    void givenAnApprovedPayment_whenALaterRejectedAttemptIsNotified_thenItStaysApproved() {
        mercadoPago.holds(new VerifiedPayment("222", NUMBER, VerifiedStatus.APPROVED, TOTAL));
        mercadoPago.holds(new VerifiedPayment("333", NUMBER, VerifiedStatus.REJECTED, TOTAL));
        service.execute(new Command(MERCADOPAGO, "222"));

        Result result = service.execute(new Command(MERCADOPAGO, "333"));

        assertThat(result).isEqualTo(new Result.Unchanged(NUMBER));
        assertThat(statusOf()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payments.paymentOf(order, MERCADOPAGO).orElseThrow().externalPaymentId()).contains("222");
    }

    @Test
    void givenAPendingPayment_whenNotified_thenNothingChanges() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.PENDING, TOTAL));

        assertThat(service.execute(new Command(MERCADOPAGO, "111"))).isEqualTo(new Result.Unchanged(NUMBER));
        assertThat(statusOf()).isEqualTo(PaymentStatus.PENDING);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAPaymentApprovedForAnotherAmount_whenNotified_thenTheOrderIsNotPaid() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.APPROVED, Money.of(100)));

        Result result = service.execute(new Command(MERCADOPAGO, "111"));

        assertThat(result).isEqualTo(new Result.AmountMismatch(NUMBER, TOTAL, Money.of(100)));
        assertThat(statusOf()).isEqualTo(PaymentStatus.PENDING);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAPaymentOfAnOrderThisStoreHasNoPaymentFor_whenNotified_thenItIsIgnored() {
        mercadoPago.holds(new VerifiedPayment("111", 9999, VerifiedStatus.APPROVED, TOTAL));

        assertThat(service.execute(new Command(MERCADOPAGO, "111"))).isEqualTo(new Result.UnknownPayment());
        assertThat(statusOf()).isEqualTo(PaymentStatus.PENDING);
        assertThat(events.published()).isEmpty();
        assertThat(notifications.recorded()).containsExactly(new InMemoryPaymentNotifications.Recorded(MERCADOPAGO,
            "111", VerifiedStatus.APPROVED, 9999));
    }

    @Test
    void givenAPaymentTheProviderDoesNotKnow_whenNotified_thenItIsIgnoredAndNotRecorded() {
        assertThat(service.execute(new Command(MERCADOPAGO, "404"))).isEqualTo(new Result.NotFoundAtProvider());
        assertThat(statusOf()).isEqualTo(PaymentStatus.PENDING);
        assertThat(notifications.recorded()).isEmpty();
    }

    @Test
    void givenTheProviderDown_whenNotified_thenItIsUnavailableAndNothingChanges() {
        mercadoPago.holds(new VerifiedPayment("111", NUMBER, VerifiedStatus.APPROVED, TOTAL));
        mercadoPago.down(true);

        assertThat(service.execute(new Command(MERCADOPAGO, "111"))).isEqualTo(new Result.ProviderUnavailable());
        assertThat(statusOf()).isEqualTo(PaymentStatus.PENDING);
        assertThat(events.published()).isEmpty();
        assertThat(notifications.recorded()).isEmpty();
    }

    @Test
    void givenTwoVerifiersForOneProvider_whenTheServiceIsBuilt_thenItRefuses() {
        assertThatThrownBy(() -> new ConfirmPaymentService(List.of(mercadoPago,
            new InMemoryPaymentVerifier(MERCADOPAGO)), null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenNoVerifierForTheProvider_whenNotified_thenItFails() {
        var withoutVerifiers = new ConfirmPaymentService(List.of(),
            new VerifiedPaymentRecorder(notifications, payments, payments, events, Clock.fixed(NOW, ZoneOffset.UTC)));

        assertThatThrownBy(() -> withoutVerifiers.execute(new Command(MERCADOPAGO, "111")))
            .isInstanceOf(IllegalStateException.class);
    }

    private PaymentStatus statusOf() {
        Optional<InMemoryPaymentStore.StoredPayment> payment = payments.paymentOf(order, MERCADOPAGO);
        return payment.orElseThrow().status();
    }
}
