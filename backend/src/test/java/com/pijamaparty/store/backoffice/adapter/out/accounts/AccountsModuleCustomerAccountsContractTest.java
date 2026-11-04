package com.pijamaparty.store.backoffice.adapter.out.accounts;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.backoffice.application.CustomerAccounts;
import com.pijamaparty.store.backoffice.application.CustomerAccountsContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the accounts module to {@code app_user}, {@code address} and {@code external_identity},
 * read by the administrator in its own transaction, so Row Level Security applies as in production (V29, V44,
 * RNF-27). Stored as the owner.
 */
@ApiTest
class AccountsModuleCustomerAccountsContractTest
    extends CustomerAccountsContractTest<AccountsModuleCustomerAccountsContractTest.AsAdmin> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, phone, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, ?, ?, ?, ?, 0, ?, ?)
        """;
    private static final String INSERT_ADDRESS = """
        insert into address (id, user_id, street, street_number, floor, apartment, city, province, postal_code,
                             is_primary, created_at, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String INSERT_IDENTITY = """
        insert into external_identity (id, user_id, provider, subject, email, created_at)
        values (?, ?, 'GOOGLE', ?, ?, ?)
        """;

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    AccountsModuleCustomerAccountsContractTest(AccountsModuleCustomerAccounts adapter,
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
    protected UUID givenCustomer(String name, String email, SavedAddress address) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(T0);
        owner.update(INSERT_USER, id, email, PASSWORD_HASH, name, "CUSTOMER", "3425551234", at, at);
        owner.update(INSERT_ADDRESS, address.id(), id, address.street(), address.number(),
            address.floor().orElse(null), address.apartment().orElse(null), address.city(), address.province(),
            address.postalCode(), address.primary(), at, at);
        owner.update(INSERT_IDENTITY, UUID.randomUUID(), id, UUID.randomUUID().toString(), email, at);
        return id;
    }

    @Override
    protected UUID givenAdministrator() {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(T0);
        owner.update(INSERT_USER, id, "owner@example.com", PASSWORD_HASH, "Dueña", "ADMIN", null, at, at);
        return id;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(AccountsModuleCustomerAccounts adapter, TransactionTemplate transactions)
        implements CustomerAccounts {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public Optional<CustomerProfile> find(UUID customerId) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.find(customerId)));
        }
    }
}
