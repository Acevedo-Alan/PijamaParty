package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.AddExternalCustomers;
import com.pijamaparty.store.accounts.application.ExternalCustomersContractTest;
import com.pijamaparty.store.accounts.application.FindUserByEmail;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Both adapters run in their caller's transaction. Here every call is its own committed transaction as SYSTEM, the
 * actor of the return from a provider (RNF-27), so the insert policy of V34 applies as in production.
 */
@ApiTest
class JdbcExternalCustomersContractTest
    extends ExternalCustomersContractTest<JdbcExternalCustomersContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JpaUserStore users;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcExternalCustomersContractTest(JdbcCustomerRegistrations customers, JpaUserStore users,
                                      PlatformTransactionManager transactionManager,
                                      PostgreSQLContainer<?> postgres) {
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.asSystem = new AsSystem(customers, users, transactions);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @Override
    protected void arrange(User user) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(user)));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenACustomerAddedVerified_whenReadFromTheTable_thenItHasNoPasswordAndNoPhone() {
        subject.add(externalCustomer("ana@example.com", true));

        assertThat(owner.queryForMap("select email_verified, password_hash, phone, role from app_user"))
            .containsEntry("email_verified", true)
            .containsEntry("password_hash", null)
            .containsEntry("phone", null)
            .containsEntry("role", "CUSTOMER");
    }

    record AsSystem(JdbcCustomerRegistrations customers, JpaUserStore users, TransactionTemplate transactions)
        implements AddExternalCustomers, FindUserByEmail {

        @Override
        public boolean add(User customer) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> customers.add(customer)));
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> users.findByEmail(email)));
        }
    }
}
