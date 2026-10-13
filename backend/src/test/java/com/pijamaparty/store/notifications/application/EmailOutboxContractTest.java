package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.InMemoryEmailOutbox.Stored;
import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link EnqueueEmails}, {@link LockDueEmails}, {@link RecordEmailOutcomes} and {@link EmailStatuses}
 * (RNF-20.7, RNF-23, UC-18): each type of email once per reference; the oldest due first; an attempt moves the email's
 * next due instant; an outcome only changes a pending email, and shows in its status.
 */
public abstract class EmailOutboxContractTest<T extends EnqueueEmails & LockDueEmails & RecordEmailOutcomes
    & EmailStatuses> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-13T15:00:30.123456Z");

    private final UUID order = UUID.randomUUID();

    /** The email as stored; empty when there is none of this type and reference. */
    protected abstract Optional<Stored> stored(EmailType type, UUID reference);

    @Test
    void givenANewEmail_whenEnqueued_thenItIsPendingAndDueAtOnce() {
        assertThat(subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW)).isTrue();
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email -> {
            assertThat(email.status()).isEqualTo("PENDING");
            assertThat(email.attempts()).isZero();
            assertThat(email.nextAttemptAt()).isEqualTo(NOW);
            assertThat(email.providerMessageId()).isEmpty();
            assertThat(email.sentAt()).isEmpty();
        });
    }

    @Test
    void givenAnEmailEnqueued_whenTheSameTypeAndReferenceIsEnqueuedAgain_thenNothingIsAdded() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);

        assertThat(subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW.plusSeconds(5))).isFalse();
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).map(Stored::nextAttemptAt).contains(NOW);
    }

    @Test
    void givenAnEmailEnqueued_whenAnotherTypeForTheSameReferenceIsEnqueued_thenBothAreThere() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);

        assertThat(subject.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW)).isTrue();
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_ADMIN, order)).isPresent();
    }

    @Test
    void givenNoEmailDue_whenLocked_thenThereIsNone() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW.plusSeconds(1));

        assertThat(subject.lockNextDue(NOW)).isEmpty();
    }

    @Test
    void givenAnEmailDue_whenLocked_thenItComesWithNoAttemptBegun() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);

        assertThat(subject.lockNextDue(NOW)).hasValueSatisfying(email -> {
            assertThat(email.type()).isEqualTo(EmailType.ORDER_PAID_CUSTOMER);
            assertThat(email.reference()).isEqualTo(order);
            assertThat(email.attempts()).isZero();
        });
    }

    @Test
    void givenAnAttemptBegun_whenLocked_thenItIsNotDueUntilItsRetryAndThenComesWithTheAttempt() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        OutboxEmail email = subject.lockNextDue(NOW).orElseThrow();
        Instant retryAt = NOW.plus(Duration.ofMinutes(1));

        subject.attempting(email.id(), 1, retryAt, NOW);
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(stored -> {
            assertThat(stored.status()).isEqualTo("PENDING");
            assertThat(stored.attempts()).isOne();
            assertThat(stored.nextAttemptAt()).isEqualTo(retryAt);
        });
        assertThat(subject.lockNextDue(retryAt.minusSeconds(1))).isEmpty();
        assertThat(subject.lockNextDue(retryAt)).map(OutboxEmail::attempts).contains(1);
    }

    @Test
    void givenTwoEmailsDue_whenLocked_thenTheOldestDueComesFirst() {
        UUID older = UUID.randomUUID();
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, older, NOW.minusSeconds(60));

        OutboxEmail first = subject.lockNextDue(NOW).orElseThrow();
        subject.attempting(first.id(), 1, NOW.plus(Duration.ofMinutes(1)), NOW);

        assertThat(first.reference()).isEqualTo(older);
        assertThat(subject.lockNextDue(NOW)).map(OutboxEmail::reference).contains(order);
    }

    @Test
    void givenAPendingEmail_whenSent_thenItIsSentWithTheProvidersIdAndNeverDueAgain() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        OutboxEmail email = subject.lockNextDue(NOW).orElseThrow();
        Instant sentAt = NOW.plusSeconds(2);

        subject.sent(email.id(), "re_0123456789", sentAt);
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(stored -> {
            assertThat(stored.status()).isEqualTo("SENT");
            assertThat(stored.providerMessageId()).contains("re_0123456789");
            assertThat(stored.sentAt()).contains(sentAt);
        });
        assertThat(subject.lockNextDue(NOW.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    void givenAPendingEmail_whenItFails_thenItIsFailedAndNeverDueAgain() {
        subject.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);
        OutboxEmail email = subject.lockNextDue(NOW).orElseThrow();

        subject.failed(email.id(), NOW.plusSeconds(2));
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_ADMIN, order)).map(Stored::status).contains("FAILED");
        assertThat(subject.lockNextDue(NOW.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    void givenEmailsOfAnOrder_whenTheirStatusIsAskedFor_thenEachTypeAnswersItsOwn() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        subject.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);
        OutboxEmail first = subject.lockNextDue(NOW).orElseThrow();
        subject.failed(first.id(), NOW.plusSeconds(1));
        persistAndForget();

        assertThat(subject.statusOf(first.type(), order)).contains(EmailStatus.FAILED);
        assertThat(subject.statusOf(first.type() == EmailType.ORDER_PAID_CUSTOMER ? EmailType.ORDER_PAID_ADMIN
            : EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.PENDING);
        assertThat(subject.statusOf(EmailType.ORDER_PAID_CUSTOMER, UUID.randomUUID())).isEmpty();
    }

    @Test
    void givenASentEmail_whenLateOutcomesArrive_thenItStaysSent() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        OutboxEmail email = subject.lockNextDue(NOW).orElseThrow();
        subject.sent(email.id(), "re_0123456789", NOW.plusSeconds(2));

        subject.failed(email.id(), NOW.plusSeconds(3));
        subject.sent(email.id(), "re_other", NOW.plusSeconds(4));
        subject.attempting(email.id(), 2, NOW.plusSeconds(5), NOW.plusSeconds(5));
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(stored -> {
            assertThat(stored.status()).isEqualTo("SENT");
            assertThat(stored.attempts()).isZero();
            assertThat(stored.providerMessageId()).contains("re_0123456789");
        });
    }
}
