package com.pijamaparty.store.ordering.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.VerifiedCustomerEmails;
import com.pijamaparty.store.ordering.application.VerifiedCustomerEmailsContractTest;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the accounts module to {@code app_user} in PostgreSQL, each call in its own transaction as
 * {@code SYSTEM}, as the sign-in links guest orders (RNF-27).
 */
@ApiTest
class AccountsModuleVerifiedCustomerEmailsContractTest
    extends VerifiedCustomerEmailsContractTest<VerifiedCustomerEmails> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AccountsModuleVerifiedCustomerEmails adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    AccountsModuleVerifiedCustomerEmailsContractTest(AccountsModuleVerifiedCustomerEmails adapter,
                                                     PlatformTransactionManager transactionManager,
                                                     PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected VerifiedCustomerEmails createSubject() {
        return customer -> CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.of(customer)));
    }

    @Override
    protected UserId givenCustomer(String email, boolean verified) {
        return user(email, verified, "CUSTOMER");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, admin_recovery_code, app_user");
    }

    @Test
    void givenTheAdministrator_whenAsked_thenThereIsNone() {
        UserId admin = user("admin@example.com", true, "ADMIN");

        assertThat(subject.of(admin)).isEmpty();
    }

    @Test
    void givenNoTransaction_whenAsked_thenItIsRefused() {
        UserId ana = givenCustomer("ana@example.com", true);

        assertThatThrownBy(() -> adapter.of(ana)).isInstanceOf(IllegalTransactionStateException.class);
    }

    private UserId user(String email, boolean verified, String role) {
        UserId user = UserId.of(UUID.randomUUID());
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, ?, ?, 'Ana', ?, 0, now(), now())
            """, user.value(), email, verified, PASSWORD_HASH, role);
        return user;
    }
}
