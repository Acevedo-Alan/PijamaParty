package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import com.pijamaparty.store.accounts.application.CountCustomers;
import com.pijamaparty.store.accounts.application.CountCustomersContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Users stored by the owner role (no policies); each count read in its own transaction as the administrator, so Row
 * Level Security applies as in production (V44, RNF-27).
 */
@ApiTest
class JdbcCustomerCountsContractTest
    extends CountCustomersContractTest<JdbcCustomerCountsContractTest.AsAdministrator> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AsAdministrator asAdministrator;
    private final JdbcTemplate owner;

    JdbcCustomerCountsContractTest(JdbcCustomerCounts adapter, PlatformTransactionManager transactionManager,
                                   PostgreSQLContainer<?> postgres) {
        this.asAdministrator = new AsAdministrator(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAdministrator createSubject() {
        return asAdministrator;
    }

    @Override
    protected void givenUser(String role, Instant registeredAt) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Cliente', ?, 0, ?, ?)
            """, id, id + "@example.com", PASSWORD_HASH, role, Timestamp.from(registeredAt),
            Timestamp.from(registeredAt));
    }

    // Customers other API tests left behind would be counted too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    /** Each count as the administrator, in its own committed transaction. */
    record AsAdministrator(JdbcCustomerCounts adapter, TransactionTemplate transactions) implements CountCustomers {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public Counts count(Instant since) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.count(since)));
        }
    }
}
