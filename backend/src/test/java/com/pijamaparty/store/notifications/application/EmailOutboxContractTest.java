package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.InMemoryEmailOutbox.Stored;
import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link EnqueueEmails}, {@link LockDueEmails}, {@link RecordEmailOutcomes}, {@link EmailStatuses},
 * {@link BounceEmails} and {@link ResendEmails} (RNF-20.7, RNF-23, UC-18, T-E3-22, T-E3-23): each version of a type
 * of email once per reference; the oldest due first; an attempt moves the email's next due instant; an outcome only
 * changes a pending email, only a sent email bounces, and the latest version shows in the status.
 */
public abstract class EmailOutboxContractTest<T extends EnqueueEmails & LockDueEmails & RecordEmailOutcomes
    & EmailStatuses & BounceEmails & ResendEmails> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-13T15:00:30.123456Z");

    private final UUID order = UUID.randomUUID();

    /** Every version of the email as stored, from the first; empty when there is none of this type and reference. */
    protected abstract List<Stored> storedVersions(EmailType type, UUID reference);

    /** The first version of the email as stored. */
    protected Optional<Stored> stored(EmailType type, UUID reference) {
        return storedVersions(type, reference).stream().findFirst();
    }

    @Test
    void givenANewEmail_whenEnqueued_thenItIsPendingAndDueAtOnce() {
        assertThat(subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW)).isTrue();
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email -> {
            assertThat(email.version()).isZero();
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
    void givenASentEmail_whenItBounces_thenItIsBouncedKeepingWhenAndWithWhichIdItWasSent() {
        UUID sent = sentEmail(EmailType.ORDER_PAID_CUSTOMER, "re_0123456789");

        assertThat(subject.bounce("re_0123456789", NOW.plusSeconds(60)))
            .contains(new BounceEmails.Bounced(EmailType.ORDER_PAID_CUSTOMER, order));
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(stored -> {
            assertThat(stored.id()).isEqualTo(sent);
            assertThat(stored.status()).isEqualTo("BOUNCED");
            assertThat(stored.providerMessageId()).contains("re_0123456789");
            assertThat(stored.sentAt()).contains(NOW.plusSeconds(2));
        });
        assertThat(subject.statusOf(EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.BOUNCED);
    }

    @Test
    void givenABouncedEmail_whenItBouncesAgain_thenNothingBounces() {
        sentEmail(EmailType.ORDER_PAID_ADMIN, "re_0123456789");
        subject.bounce("re_0123456789", NOW.plusSeconds(60));

        assertThat(subject.bounce("re_0123456789", NOW.plusSeconds(120))).isEmpty();
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_ADMIN, order)).map(Stored::status).contains("BOUNCED");
    }

    @Test
    void givenNoSentEmailWithTheProvidersId_whenItBounces_thenNothingBouncesAndAPendingEmailStaysPending() {
        sentEmail(EmailType.ORDER_PAID_CUSTOMER, "re_0123456789");
        subject.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);

        assertThat(subject.bounce("re_unknown", NOW.plusSeconds(60))).isEmpty();
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).map(Stored::status).contains("SENT");
        assertThat(stored(EmailType.ORDER_PAID_ADMIN, order)).map(Stored::status).contains("PENDING");
    }

    @Test
    void givenABouncedEmail_whenLateOutcomesArrive_thenItStaysBounced() {
        UUID email = sentEmail(EmailType.ORDER_PAID_CUSTOMER, "re_0123456789");
        subject.bounce("re_0123456789", NOW.plusSeconds(60));

        subject.failed(email, NOW.plusSeconds(61));
        subject.sent(email, "re_other", NOW.plusSeconds(62));
        persistAndForget();

        assertThat(stored(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(stored -> {
            assertThat(stored.status()).isEqualTo("BOUNCED");
            assertThat(stored.providerMessageId()).contains("re_0123456789");
        });
    }

    /** An email of the order sent at {@code NOW + 2 s} with the provider's id; answers its id. */
    private UUID sentEmail(EmailType type, String providerMessageId) {
        subject.enqueue(type, order, NOW);
        OutboxEmail email = subject.lockNextDue(NOW).orElseThrow();
        subject.sent(email.id(), providerMessageId, NOW.plusSeconds(2));
        return email.id();
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

    @Test
    void givenAnEmailNotAttemptedYet_whenLockedToResend_thenItIsThere() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);

        assertThat(subject.lockUnattempted(EmailType.ORDER_PAID_CUSTOMER, order)).isTrue();
    }

    @Test
    void givenNoEmailOrOnlyOfAnotherType_whenLockedToResend_thenThereIsNone() {
        subject.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);

        assertThat(subject.lockUnattempted(EmailType.ORDER_PAID_CUSTOMER, order)).isFalse();
        assertThat(subject.lockUnattempted(EmailType.ORDER_PAID_CUSTOMER, UUID.randomUUID())).isFalse();
    }

    @Test
    void givenAnEmailWithAnAttemptBegun_whenLockedToResend_thenThereIsNone() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        OutboxEmail email = subject.lockNextDue(NOW).orElseThrow();
        subject.attempting(email.id(), 1, NOW.plus(Duration.ofMinutes(1)), NOW);

        assertThat(subject.lockUnattempted(EmailType.ORDER_PAID_CUSTOMER, order)).isFalse();
    }

    @Test
    void givenASentOrAFailedEmail_whenLockedToResend_thenThereIsNone() {
        sentEmail(EmailType.ORDER_PAID_CUSTOMER, "re_0123456789");
        subject.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);
        subject.failed(subject.lockNextDue(NOW).orElseThrow().id(), NOW);

        assertThat(subject.lockUnattempted(EmailType.ORDER_PAID_CUSTOMER, order)).isFalse();
        assertThat(subject.lockUnattempted(EmailType.ORDER_PAID_ADMIN, order)).isFalse();
    }

    @Test
    void givenASentEmail_whenResentTwice_thenVersionsOneAndTwoArePendingAndDueAtOnce() {
        sentEmail(EmailType.ORDER_PAID_CUSTOMER, "re_0123456789");
        Instant later = NOW.plusSeconds(600);

        assertThat(subject.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, order, later)).isOne();
        assertThat(subject.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, order, later)).isEqualTo(2);
        persistAndForget();

        assertThat(storedVersions(EmailType.ORDER_PAID_CUSTOMER, order)).satisfiesExactly(
            first -> assertThat(first.status()).isEqualTo("SENT"),
            second -> {
                assertThat(second.version()).isOne();
                assertThat(second.status()).isEqualTo("PENDING");
                assertThat(second.attempts()).isZero();
                assertThat(second.nextAttemptAt()).isEqualTo(later);
                assertThat(second.providerMessageId()).isEmpty();
            },
            third -> assertThat(third.version()).isEqualTo(2));
    }

    @Test
    void givenNoEmail_whenResent_thenItIsVersionOne() {
        assertThat(subject.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, order, NOW)).isOne();
        persistAndForget();

        assertThat(storedVersions(EmailType.ORDER_PAID_CUSTOMER, order)).singleElement()
            .satisfies(email -> assertThat(email.version()).isOne());
    }

    @Test
    void givenAResentEmail_whenTheFirstVersionIsEnqueuedAgain_thenNothingIsAdded() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        subject.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, order, NOW);

        assertThat(subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW)).isFalse();
        persistAndForget();

        assertThat(storedVersions(EmailType.ORDER_PAID_CUSTOMER, order)).hasSize(2);
    }

    @Test
    void givenAFailedEmailResent_whenItsStatusIsAskedFor_thenTheLatestVersionAnswers() {
        subject.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        subject.failed(subject.lockNextDue(NOW).orElseThrow().id(), NOW);

        subject.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, order, NOW.plusSeconds(60));
        persistAndForget();

        assertThat(subject.statusOf(EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.PENDING);
    }

    @Test
    void givenAResentEmail_whenTheFirstVersionBouncesLate_thenItBouncesAndTheLatestVersionStillAnswers() {
        sentEmail(EmailType.ORDER_PAID_CUSTOMER, "re_0123456789");
        subject.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, order, NOW.plusSeconds(60));
        OutboxEmail resent = subject.lockNextDue(NOW.plusSeconds(60)).orElseThrow();
        subject.sent(resent.id(), "re_resent", NOW.plusSeconds(62));

        assertThat(subject.bounce("re_0123456789", NOW.plusSeconds(120)))
            .contains(new BounceEmails.Bounced(EmailType.ORDER_PAID_CUSTOMER, order));
        persistAndForget();

        assertThat(storedVersions(EmailType.ORDER_PAID_CUSTOMER, order)).extracting(Stored::status)
            .containsExactly("BOUNCED", "SENT");
        assertThat(subject.statusOf(EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.SENT);
    }
}
