package com.pijamaparty.store.backoffice.adapter.out.notifications;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.BuyerEmails;
import com.pijamaparty.store.backoffice.application.BuyerEmailsContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the notifications module to {@code email_outbox}, read by the administrator in its own
 * transaction, so Row Level Security applies as in production (RNF-27). Emails stored as the owner.
 */
@ApiTest
class NotificationsModuleBuyerEmailsContractTest extends BuyerEmailsContractTest<BuyerEmails> {

    private static final String INSERT = """
        insert into email_outbox (id, type, reference, status, attempts, next_attempt_at, created_at, updated_at)
        values (gen_random_uuid(), 'ORDER_PAID_CUSTOMER', ?, ?, 5, now(), now(), now())
        """;

    private final NotificationsModuleBuyerEmails adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    NotificationsModuleBuyerEmailsContractTest(NotificationsModuleBuyerEmails adapter,
                                               PlatformTransactionManager transactionManager,
                                               PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected BuyerEmails createSubject() {
        Actor admin = Actor.admin(UserId.of(UUID.randomUUID()));
        return orderId -> CurrentActor.callAs(admin,
            () -> transactions.execute(status -> adapter.problemOf(orderId)));
    }

    @Override
    protected void givenFailed(UUID orderId) {
        owner.update(INSERT, orderId, "FAILED");
    }

    @Override
    protected void givenBounced(UUID orderId) {
        owner.update("""
            insert into email_outbox (id, type, reference, status, attempts, next_attempt_at, provider_message_id,
                                      created_at, sent_at, updated_at)
            values (gen_random_uuid(), 'ORDER_PAID_CUSTOMER', ?, 'BOUNCED', 1, now(), 're_1', now(), now(), now())
            """, orderId);
    }

    @Override
    protected void givenPending(UUID orderId) {
        owner.update(INSERT, orderId, "PENDING");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_outbox");
    }
}
