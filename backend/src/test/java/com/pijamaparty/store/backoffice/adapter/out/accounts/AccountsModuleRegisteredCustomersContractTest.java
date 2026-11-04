package com.pijamaparty.store.backoffice.adapter.out.accounts;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerDirectory.Listing;
import com.pijamaparty.store.backoffice.application.RegisteredCustomers;
import com.pijamaparty.store.backoffice.application.RegisteredCustomersContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the accounts module to {@code app_user}, read by the administrator in its own transaction,
 * so Row Level Security applies as in production (V44, RNF-27). Users stored as the owner.
 */
@ApiTest
class AccountsModuleRegisteredCustomersContractTest
    extends RegisteredCustomersContractTest<AccountsModuleRegisteredCustomersContractTest.AsAdmin> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    AccountsModuleRegisteredCustomersContractTest(AccountsModuleRegisteredCustomers adapter,
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
    protected UUID givenCustomer(String name, String email, Instant registeredAt) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, ?, 'CUSTOMER', 0, ?, ?)
            """, id, email, PASSWORD_HASH, name, Timestamp.from(registeredAt), Timestamp.from(registeredAt));
        return id;
    }

    // Customers other API tests left behind would be listed too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(AccountsModuleRegisteredCustomers adapter, TransactionTemplate transactions)
        implements RegisteredCustomers {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public Listing list(Optional<String> search, Optional<LocalDate> from, Optional<LocalDate> to, int page) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.list(search, from, to,
                page)));
        }
    }
}
