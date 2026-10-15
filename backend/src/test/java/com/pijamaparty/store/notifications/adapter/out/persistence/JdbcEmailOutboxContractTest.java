package com.pijamaparty.store.notifications.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.application.BounceEmails;
import com.pijamaparty.store.notifications.application.EmailOutboxContractTest;
import com.pijamaparty.store.notifications.application.EmailStatuses;
import com.pijamaparty.store.notifications.application.EnqueueEmails;
import com.pijamaparty.store.notifications.application.InMemoryEmailOutbox.Stored;
import com.pijamaparty.store.notifications.application.LockDueEmails;
import com.pijamaparty.store.notifications.application.RecordEmailOutcomes;
import com.pijamaparty.store.notifications.application.ResendEmails;
import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The outbox in {@code email_outbox}, as the system, each call in its own committed transaction (enqueueing inside
 * one, as the payment's; resending as the system too, its buyer's policies are tested in
 * {@link EmailOutboxRowLevelSecurityTest}), so Row Level Security applies as in production (RNF-27). Read back as the
 * owner.
 * Its Row Level Security policies are {@link EmailOutboxRowLevelSecurityTest}.
 */
@ApiTest
class JdbcEmailOutboxContractTest extends EmailOutboxContractTest<JdbcEmailOutboxContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcEmailOutbox adapter;
    private final JdbcTemplate owner;

    JdbcEmailOutboxContractTest(JdbcEmailOutbox adapter, PlatformTransactionManager transactionManager,
                                PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.asSystem = new AsSystem(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @Override
    protected List<Stored> storedVersions(EmailType type, UUID reference) {
        return owner.query("select * from email_outbox where type = ? and reference = ? order by version",
            (row, n) -> new Stored(
                row.getObject("id", UUID.class), EmailType.valueOf(row.getString("type")),
                row.getObject("reference", UUID.class), row.getInt("version"), row.getString("status"),
                row.getInt("attempts"),
                row.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(row.getString("provider_message_id")),
                Optional.ofNullable(row.getObject("sent_at", OffsetDateTime.class)).map(OffsetDateTime::toInstant)),
            type.name(), reference);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_outbox");
    }

    @Test
    void givenNoTransaction_whenAnEmailIsEnqueued_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(
                () -> adapter.enqueue(EmailType.ORDER_PAID_CUSTOMER, UUID.randomUUID(), NOW)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void givenNoTransaction_whenAnEmailBounces_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(() -> adapter.bounce("re_0123456789", NOW)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void givenNoTransaction_whenAnEmailIsResent_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(
                () -> adapter.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, UUID.randomUUID(), NOW)))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> CurrentActor.callAsSystem(
                () -> adapter.lockUnattempted(EmailType.ORDER_PAID_CUSTOMER, UUID.randomUUID())))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void givenNoTransaction_whenTheNextEmailDueIsLocked_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(() -> adapter.lockNextDue(NOW)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    /**
     * As the job and the payment run it: the system, enqueueing inside a transaction, the rest in their own. The status
     * is read as the back office reads it: the administrator, in a read transaction.
     */
    record AsSystem(JdbcEmailOutbox adapter, TransactionTemplate transactions)
        implements EnqueueEmails, LockDueEmails, RecordEmailOutcomes, EmailStatuses, BounceEmails, ResendEmails {

        @Override
        public boolean lockUnattempted(EmailType type, UUID reference) {
            return Boolean.TRUE.equals(CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.lockUnattempted(type, reference))));
        }

        @Override
        public int enqueueNextVersion(EmailType type, UUID reference, Instant at) {
            Integer version = CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.enqueueNextVersion(type, reference, at)));
            return version == null ? -1 : version;
        }

        /** As the bounce webhook runs it: the system, inside a transaction. */
        @Override
        public Optional<Bounced> bounce(String providerMessageId, Instant at) {
            return CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.bounce(providerMessageId, at)));
        }

        @Override
        public Optional<EmailStatus> statusOf(EmailType type, UUID reference) {
            return CurrentActor.callAs(Actor.admin(UserId.of(UUID.randomUUID())),
                () -> transactions.execute(status -> adapter.statusOf(type, reference)));
        }

        @Override
        public boolean enqueue(EmailType type, UUID reference, Instant at) {
            return Boolean.TRUE.equals(CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.enqueue(type, reference, at))));
        }

        @Override
        public Optional<OutboxEmail> lockNextDue(Instant now) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.lockNextDue(now)));
        }

        @Override
        public void attempting(UUID email, int attempts, Instant retryAt, Instant at) {
            CurrentActor.runAsSystem(() -> adapter.attempting(email, attempts, retryAt, at));
        }

        @Override
        public void sent(UUID email, String providerMessageId, Instant at) {
            CurrentActor.runAsSystem(() -> adapter.sent(email, providerMessageId, at));
        }

        @Override
        public void failed(UUID email, Instant at) {
            CurrentActor.runAsSystem(() -> adapter.failed(email, at));
        }
    }
}
