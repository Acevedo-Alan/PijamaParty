package com.pijamaparty.store.notifications.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.application.EmailEvents;
import com.pijamaparty.store.notifications.application.EmailEventsContractTest;
import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call commits in its own transaction as {@code SYSTEM}, as the webhook runs, so Row Level Security applies as in
 * production (RNF-27). Read back as the owner. Its Row Level Security policies are
 * {@link EmailEventRowLevelSecurityTest}.
 */
@ApiTest
class JdbcEmailEventStoreContractTest extends EmailEventsContractTest<JdbcEmailEventStoreContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcEmailEventStore adapter;
    private final JdbcTemplate owner;

    JdbcEmailEventStoreContractTest(JdbcEmailEventStore adapter, PlatformTransactionManager transactionManager,
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

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_event");
    }

    @Test
    void givenANewNotification_whenRecorded_thenItsEventTypeEmailAndTimeAreStored() {
        subject.recordFirst(RESEND, "msg_2Kk9", EmailEventType.SUPPRESSED, "4ef9a417-02e9-4d39-ad75-9611e0fcc33c",
            NOW);

        Map<String, Object> row = owner.queryForMap("select * from email_event");
        assertThat(row).containsEntry("provider", "RESEND").containsEntry("event_id", "msg_2Kk9")
            .containsEntry("type", "SUPPRESSED")
            .containsEntry("provider_message_id", "4ef9a417-02e9-4d39-ad75-9611e0fcc33c");
        assertThat(owner.queryForObject("select received_at from email_event", OffsetDateTime.class).toInstant())
            .isEqualTo(NOW);
    }

    @Test
    void givenATransactionThatRecordedANotificationAndFailed_whenItArrivesAgain_thenItIsToBeProcessed() {
        assertThatThrownBy(() -> asSystem.inTransaction(() -> {
            adapter.recordFirst(RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW);
            throw new IllegalStateException("the email could not be marked");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(subject.recordFirst(RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW.plusSeconds(5)))
            .isTrue();
    }

    @Test
    void givenANotificationBeingRecordedByAnotherTransaction_whenRecordedAgain_thenItWaitsAndWasAlreadyProcessed()
        throws Exception {
        var recorded = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> asSystem.inTransaction(() -> {
                boolean inserted = adapter.recordFirst(RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW);
                recorded.countDown();
                await(release);
                return inserted;
            }));
            assertThat(recorded.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() ->
                subject.recordFirst(RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW));

            Thread.sleep(300);
            assertThat(second.isDone()).isFalse();
            release.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(owner.queryForObject("select count(*) from email_event", Long.class)).isOne();
    }

    @Test
    void givenNoTransaction_whenANotificationIsRecorded_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(() ->
            adapter.recordFirst(RESEND, "msg_1", EmailEventType.COMPLAINT, "re_1", NOW)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The system, as the webhook runs (CurrentActor.callAsSystem); each call its own transaction. */
    record AsSystem(JdbcEmailEventStore adapter, TransactionTemplate transactions) implements EmailEvents {

        <R> R inTransaction(Supplier<R> work) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> work.get()));
        }

        @Override
        public boolean recordFirst(MailProvider provider, String eventId, EmailEventType type,
                                   String providerMessageId, Instant receivedAt) {
            return Boolean.TRUE.equals(inTransaction(() ->
                adapter.recordFirst(provider, eventId, type, providerMessageId, receivedAt)));
        }
    }
}
