package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SignInProvider;
import com.pijamaparty.store.accounts.application.LoadCustomerProfile;
import com.pijamaparty.store.accounts.application.LoadCustomerProfileContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Users, addresses and linked accounts stored by the owner role (no policies); each profile read in its own
 * transaction as the administrator, so Row Level Security applies as in production (V29, V44, RNF-27).
 */
@ApiTest
class JdbcCustomerProfilesContractTest
    extends LoadCustomerProfileContractTest<JdbcCustomerProfilesContractTest.AsAdministrator> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AsAdministrator asAdministrator;
    private final JdbcTemplate owner;

    JdbcCustomerProfilesContractTest(JdbcCustomerProfiles adapter, PlatformTransactionManager transactionManager,
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
    protected void givenUser(UUID id, String name, String email, boolean emailVerified, String phone, String role,
                             Instant registeredAt) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, phone, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
            """, id, email, emailVerified, PASSWORD_HASH, name, role, phone, Timestamp.from(registeredAt),
            Timestamp.from(registeredAt));
    }

    @Override
    protected void givenAddress(UUID userId, SavedAddress address, Instant addedAt) {
        owner.update("""
            insert into address (id, user_id, street, street_number, floor, apartment, city, province, postal_code,
                                 is_primary, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, address.id(), userId, address.street(), address.number(), address.floor().orElse(null),
            address.apartment().orElse(null), address.city(), address.province(), address.postalCode(),
            address.primary(), Timestamp.from(addedAt), Timestamp.from(addedAt));
    }

    @Override
    protected void givenLinkedAccount(UUID userId, SignInProvider provider, Instant linkedAt) {
        owner.update("""
            insert into external_identity (id, user_id, provider, subject, email, created_at)
            values (?, ?, ?, ?, 'external@example.com', ?)
            """, UUID.randomUUID(), userId, provider.name(), UUID.randomUUID().toString(), Timestamp.from(linkedAt));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    /** Each profile as the administrator, in its own committed transaction. */
    record AsAdministrator(JdbcCustomerProfiles adapter, TransactionTemplate transactions)
        implements LoadCustomerProfile {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public Optional<CustomerProfile> find(UUID customerId) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.find(customerId)));
        }
    }
}
