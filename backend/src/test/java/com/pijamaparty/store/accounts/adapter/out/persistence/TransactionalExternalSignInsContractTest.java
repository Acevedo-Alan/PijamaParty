package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.ExternalCustomersContractTest;
import com.pijamaparty.store.accounts.application.ExternalSignInsContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;

/** The adapter opens its own transaction as SYSTEM, whatever the visitor was, and rolls it back on a failure. */
@ApiTest
class TransactionalExternalSignInsContractTest extends ExternalSignInsContractTest<TransactionalExternalSignIns> {

    private final TransactionalExternalSignIns adapter;
    private final JdbcCustomerRegistrations customers;
    private final JdbcTemplate owner;

    TransactionalExternalSignInsContractTest(TransactionalExternalSignIns adapter,
                                             JdbcCustomerRegistrations customers, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.customers = customers;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected TransactionalExternalSignIns createSubject() {
        return adapter;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenAnAnonymousVisitor_whenSigningIn_thenTheBlockRunsAsTheSystemInATransaction() {
        var visitor = Actor.anonymous(Optional.empty(), Optional.empty());

        Actor actor = CurrentActor.callAs(visitor, () -> subject.signingIn(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return CurrentActor.get().orElseThrow();
        }));

        assertThat(actor).isEqualTo(Actor.system());
    }

    @Test
    void givenABlockThatFailsAfterCreatingACustomer_whenSigningIn_thenTheCustomerIsNotKept() {
        assertThatThrownBy(() -> subject.signingIn(() -> {
            customers.add(ExternalCustomersContractTest.externalCustomer("ana@example.com", true));
            throw new IllegalStateException("the provider account was taken");
        })).hasMessageContaining("the provider account was taken");

        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isZero();
    }
}
