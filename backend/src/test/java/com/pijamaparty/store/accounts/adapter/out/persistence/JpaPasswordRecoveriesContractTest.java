package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.CustomerRegistrationsContractTest;
import com.pijamaparty.store.accounts.application.PasswordRecoveriesContractTest;
import com.pijamaparty.store.accounts.application.UserStoreContractTest;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
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
 * The adapter runs its own transaction as {@code SYSTEM}, whatever the caller's actor, so the contract runs on
 * committed data.
 */
@ApiTest
class JpaPasswordRecoveriesContractTest extends PasswordRecoveriesContractTest<JpaPasswordRecoveries> {

    private final JpaPasswordRecoveries adapter;
    private final JpaUserStore users;
    private final UserJpaRepository rows;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JpaPasswordRecoveriesContractTest(JpaPasswordRecoveries adapter, JpaUserStore users, UserJpaRepository rows,
                                      PlatformTransactionManager transactionManager,
                                      PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.users = users;
        this.rows = rows;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected JpaPasswordRecoveries createSubject() {
        return adapter;
    }

    @Override
    protected void arrange(User user) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(user)));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, admin_recovery_code, app_user");
    }

    @Test
    void givenAGuestRequest_whenRequesting_thenItStillFindsTheCustomerAsTheSystem() {
        arrange(CustomerRegistrationsContractTest.customer("ana@example.com"));
        var found = new boolean[1];

        CurrentActor.runAs(Actor.anonymous(Optional.empty(), Optional.empty()),
            () -> subject.requesting(new Email("ana@example.com"), user -> found[0] = user.isPresent()));

        assertThat(found[0]).isTrue();
    }

    @Test
    void givenARequest_whenItTriesToChangeTheUser_thenTheDatabaseRefusesIt() {
        User customer = CustomerRegistrationsContractTest.customer("ana@example.com");
        arrange(customer);

        assertThatThrownBy(() -> subject.requesting(customer.email(), user -> {
            User loaded = user.orElseThrow();
            loaded.verifyEmail(UserStoreContractTest.NOW);
            users.save(loaded);
            rows.flush();
        })).isInstanceOf(RuntimeException.class);
    }
}
