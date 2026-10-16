package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.CustomerRegistrationsContractTest;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
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

/** The adapter commits its own transaction as {@code SYSTEM}, so the contract runs on committed data. */
@ApiTest
class JdbcCustomerRegistrationsContractTest extends CustomerRegistrationsContractTest<JdbcCustomerRegistrations> {

    private final JdbcCustomerRegistrations adapter;
    private final JpaUserStore users;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcCustomerRegistrationsContractTest(JdbcCustomerRegistrations adapter, JpaUserStore users,
                                          PlatformTransactionManager transactionManager,
                                          PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected JdbcCustomerRegistrations createSubject() {
        return adapter;
    }

    @Override
    protected void arrange(User user) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(user)));
    }

    @Override
    protected Optional<User> stored(UserId id) {
        return CurrentActor.callAsSystem(() -> transactions.execute(status -> users.findById(id)));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, admin_recovery_code, app_user");
    }

    @Test
    void givenTheFollowUpFails_whenRegistering_thenTheAccountIsNotKept() {
        User customer = customer("ana@example.com");

        assertThatThrownBy(() -> subject.register(customer, () -> {
            throw new IllegalStateException("the outbox is down");
        })).hasMessageContaining("the outbox is down");

        assertThat(stored(customer.id())).isEmpty();
    }
}
