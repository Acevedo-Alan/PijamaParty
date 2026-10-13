package com.pijamaparty.store.notifications.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.application.EmailOutboxContractTest;
import com.pijamaparty.store.notifications.application.EnqueueEmails;
import com.pijamaparty.store.notifications.application.InMemoryEmailOutbox.Stored;
import com.pijamaparty.store.notifications.application.LockDueEmails;
import com.pijamaparty.store.notifications.application.RecordEmailOutcomes;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The outbox in {@code email_outbox}, as the system, each call in its own committed transaction (enqueueing inside
 * one, as the payment's), so Row Level Security applies as in production (RNF-27). Read back as the owner.
 */
@ApiTest
class JdbcEmailOutboxContractTest extends EmailOutboxContractTest<JdbcEmailOutboxContractTest.AsSystem> {

    private static final String INSERT = """
        insert into email_outbox (id, type, reference, status, attempts, next_attempt_at, created_at, updated_at)
        values (gen_random_uuid(), 'ORDER_PAID_CUSTOMER', gen_random_uuid(), 'PENDING', 0, now(), now(), now())
        """;

    private final AsSystem asSystem;
    private final JdbcEmailOutbox adapter;
    private final JdbcTemplate owner;
    private final RlsPolicyTester rls;

    JdbcEmailOutboxContractTest(JdbcEmailOutbox adapter, PlatformTransactionManager transactionManager,
                                JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.asSystem = new AsSystem(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @Override
    protected Optional<Stored> stored(EmailType type, UUID reference) {
        return owner.query("select * from email_outbox where type = ? and reference = ?", (row, n) -> new Stored(
                row.getObject("id", UUID.class), EmailType.valueOf(row.getString("type")),
                row.getObject("reference", UUID.class), row.getString("status"), row.getInt("attempts"),
                row.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(row.getString("provider_message_id")),
                Optional.ofNullable(row.getObject("sent_at", OffsetDateTime.class)).map(OffsetDateTime::toInstant)),
            type.name(), reference).stream().findFirst();
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
    void givenNoTransaction_whenTheNextEmailDueIsLocked_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(() -> adapter.lockNextDue(NOW)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void givenTheOutbox_whenCheckingItsProtection_thenRowLevelSecurityIsEnabledAndForced() {
        assertThat(owner.queryForMap("select relrowsecurity, relforcerowsecurity from pg_class "
            + "where oid = 'email_outbox'::regclass"))
            .containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
    }

    @Test
    void givenAnyoneButTheSystem_whenEnqueueing_thenRowLevelSecurityRefusesIt() {
        for (Actor actor : new Actor[] {RlsPolicyTester.CUSTOMER_A, RlsPolicyTester.CUSTOMER_B, RlsPolicyTester.GUEST,
            RlsPolicyTester.ADMIN}) {
            assertThatThrownBy(() -> rls.as(actor, sql -> sql.update(INSERT))).as(actor.toString())
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        }
    }

    @Test
    void givenAnEmail_whenReadByEachActor_thenOnlyTheSystemAndTheAdministratorSeeIt() {
        rls.as(RlsPolicyTester.SYSTEM, sql -> sql.update(INSERT));

        for (Actor actor : new Actor[] {RlsPolicyTester.CUSTOMER_A, RlsPolicyTester.CUSTOMER_B,
            RlsPolicyTester.GUEST}) {
            assertThat(emailsSeenBy(actor)).as(actor.toString()).isZero();
        }
        for (Actor actor : new Actor[] {RlsPolicyTester.ADMIN, RlsPolicyTester.SYSTEM}) {
            assertThat(emailsSeenBy(actor)).as(actor.toString()).isOne();
        }
    }

    @Test
    void givenAnEmail_whenTheAdministratorTriesToChangeIt_thenNothingChanges() {
        rls.as(RlsPolicyTester.SYSTEM, sql -> sql.update(INSERT));

        Integer changed = rls.as(RlsPolicyTester.ADMIN,
            sql -> sql.update("update email_outbox set status = 'FAILED'"));

        assertThat(changed).isZero();
        assertThatThrownBy(() -> rls.as(RlsPolicyTester.SYSTEM, sql -> sql.update("delete from email_outbox")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }

    private long emailsSeenBy(Actor actor) {
        Long count = rls.as(actor, sql -> sql.queryForObject("select count(*) from email_outbox", Long.class));
        return count == null ? 0 : count;
    }

    /** As the job and the payment run it: the system, enqueueing inside a transaction, the rest in their own. */
    record AsSystem(JdbcEmailOutbox adapter, TransactionTemplate transactions)
        implements EnqueueEmails, LockDueEmails, RecordEmailOutcomes {

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
