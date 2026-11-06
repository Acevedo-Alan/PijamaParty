package com.pijamaparty.store.backoffice.adapter.out.accounts;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import com.pijamaparty.store.backoffice.application.CustomerFigures;
import com.pijamaparty.store.backoffice.application.CustomerFiguresContractTest;
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
 * Through the public API of the accounts module to {@code app_user}, counted by the administrator in its own
 * transaction, so Row Level Security applies as in production (V44, RNF-27). Users stored as the owner.
 */
@ApiTest
class AccountsModuleCustomerFiguresContractTest
    extends CustomerFiguresContractTest<AccountsModuleCustomerFiguresContractTest.AsAdmin> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, ?, 'Cliente', ?, 0, ?, ?)
        """;

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    AccountsModuleCustomerFiguresContractTest(AccountsModuleCustomerFigures adapter,
                                              PlatformTransactionManager transactionManager,
                                              PostgreSQLContainer<?> postgres) {
        this.asAdmin = new AsAdmin(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAdmin createSubject() {
        return asAdmin;
    }

    @Override
    protected void givenCustomer(Instant registeredAt) {
        stored("CUSTOMER", registeredAt);
    }

    @Override
    protected void givenAdministrator(Instant registeredAt) {
        stored("ADMIN", registeredAt);
    }

    private void stored(String role, Instant registeredAt) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(registeredAt);
        owner.update(INSERT_USER, id, id + "@example.com", PASSWORD_HASH, role, at, at);
    }

    // Customers other API tests left behind would be counted too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(AccountsModuleCustomerFigures adapter, TransactionTemplate transactions)
        implements CustomerFigures {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public Counts count(Instant since) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.count(since)));
        }
    }
}
