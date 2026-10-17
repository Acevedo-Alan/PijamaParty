package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.RecoveryCodes;
import com.pijamaparty.store.accounts.application.RecoveryCodesContractTest;
import com.pijamaparty.store.accounts.application.UserStoreContractTest;
import com.pijamaparty.store.accounts.domain.RecoveryCodeHash;
import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** On PostgreSQL, each call in its own {@code SYSTEM} transaction, as the sign-in runs it (RNF-27). */
@ApiTest
class JdbcRecoveryCodesContractTest extends RecoveryCodesContractTest<JdbcRecoveryCodesContractTest.AsSystem> {

    private final JdbcRecoveryCodes adapter;
    private final JpaUserStore users;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcRecoveryCodesContractTest(JdbcRecoveryCodes adapter, JpaUserStore users,
                                  PlatformTransactionManager transactionManager, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return new AsSystem(adapter, transactions);
    }

    @Override
    protected UserId givenAdministrator() {
        User administrator = UserStoreContractTest.administrator();
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(administrator)));
        return administrator.id();
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, admin_recovery_code, app_user");
    }

    @Test
    void givenStoredCodes_whenTheAdministratorOrAVisitorReadsThem_thenRowLevelSecurityShowsNothing() {
        UserId administrator = givenAdministratorWithCodes();

        for (Actor actor : List.of(Actor.admin(administrator), Actor.anonymous(Optional.empty(), Optional.empty()))) {
            Integer visible = CurrentActor.callAs(actor, () -> transactions.execute(status ->
                adapter.unused(administrator)));
            assertThat(visible).as("codes visible to %s", actor.role()).isZero();
        }
        assertThat(subject.unused(administrator)).isEqualTo(1);
    }

    @Test
    void givenAnotherActor_whenWritingCodes_thenRowLevelSecurityRefuses() {
        UserId administrator = givenAdministratorWithCodes();
        RecoveryCodeHash code = new SecondFactorCode.Recovery("BBBBB00001").hash();

        assertThatThrownBy(() -> CurrentActor.runAs(Actor.admin(administrator), () ->
            transactions.executeWithoutResult(status -> adapter.replace(administrator, List.of(code)))))
            .rootCause().isInstanceOf(java.sql.SQLException.class)
            .extracting(cause -> ((java.sql.SQLException) cause).getSQLState())
            .as("insufficient privilege: the row-level security policy refuses it").isEqualTo("42501");
    }

    /** The administrator the contract stored before each test, now with one code. */
    private UserId givenAdministratorWithCodes() {
        UserId administrator = UserId.of(owner.queryForObject("select id from app_user", java.util.UUID.class));
        subject.replace(administrator, List.of(new SecondFactorCode.Recovery("AAAAA00001").hash()));
        return administrator;
    }

    /** Each call in its own committed transaction as {@code SYSTEM}. */
    record AsSystem(JdbcRecoveryCodes adapter, TransactionTemplate transactions) implements RecoveryCodes {

        private <R> R system(Supplier<R> call) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> call.get()));
        }

        @Override
        public void replace(UserId user, List<RecoveryCodeHash> codes) {
            system(() -> {
                adapter.replace(user, codes);
                return null;
            });
        }

        @Override
        public boolean consume(UserId user, RecoveryCodeHash code, Instant now) {
            return system(() -> adapter.consume(user, code, now));
        }

        @Override
        public int unused(UserId user) {
            return system(() -> adapter.unused(user));
        }

        @Override
        public void deleteAll(UserId user) {
            system(() -> {
                adapter.deleteAll(user);
                return null;
            });
        }
    }
}
