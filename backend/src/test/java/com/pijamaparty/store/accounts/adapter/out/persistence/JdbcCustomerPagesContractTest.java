package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import com.pijamaparty.store.accounts.application.CustomerFilter;
import com.pijamaparty.store.accounts.application.CustomerPages;
import com.pijamaparty.store.accounts.application.CustomerPagesContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Users stored by the owner role (no policies); each page read in its own transaction as the administrator, so Row
 * Level Security applies as in production (V44, RNF-27).
 */
@ApiTest
class JdbcCustomerPagesContractTest extends CustomerPagesContractTest<JdbcCustomerPagesContractTest.AsAdministrator> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AsAdministrator asAdministrator;
    private final JdbcTemplate owner;

    JdbcCustomerPagesContractTest(JdbcCustomerPages adapter, PlatformTransactionManager transactionManager,
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
    protected void givenUser(UUID id, String name, String email, String role, Instant registeredAt) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, ?, ?, 0, ?, ?)
            """, id, email, PASSWORD_HASH, name, role, Timestamp.from(registeredAt), Timestamp.from(registeredAt));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    /** Each page as the administrator, in its own committed transaction. */
    record AsAdministrator(JdbcCustomerPages adapter, TransactionTemplate transactions) implements CustomerPages {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public List<Customer> page(CustomerFilter filter, int offset, int limit) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.page(filter, offset, limit)));
        }
    }
}
