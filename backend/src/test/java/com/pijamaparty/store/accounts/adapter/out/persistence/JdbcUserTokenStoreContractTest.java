package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.CustomerRegistrationsContractTest;
import com.pijamaparty.store.accounts.application.UserTokensContractTest;
import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Issuing joins the caller's transaction, so the contract issues as {@code SYSTEM} in a committed one; consuming
 * commits its own. The last tests check that the database binds the verification and the reset to the consumed token
 * (V29, V31).
 */
@ApiTest
class JdbcUserTokenStoreContractTest extends UserTokensContractTest<JdbcUserTokenStore> {

    private static final PasswordHash NEW_HASH = new PasswordHash("{bcrypt}$2a$12$" + "n".repeat(53));

    private final JdbcUserTokenStore adapter;
    private final JpaUserStore users;
    private final UserJpaRepository rows;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcUserTokenStoreContractTest(JdbcUserTokenStore adapter, JpaUserStore users, UserJpaRepository rows,
                                   PlatformTransactionManager transactionManager, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.users = users;
        this.rows = rows;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected JdbcUserTokenStore createSubject() {
        return adapter;
    }

    @Override
    protected void arrange(User user) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(user)));
    }

    @Override
    protected void issue(UserToken token) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> subject.add(token)));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenTheTokenUsedFirst_whenTheEmailIsVerifiedInTheSameTransaction_thenTheDatabaseAcceptsIt() {
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.EMAIL_VERIFICATION));

        subject.consuming(link, token -> {
            token.orElseThrow().use(UserTokenPurpose.EMAIL_VERIFICATION, NOW);
            subject.markUsed(token.orElseThrow());
            verifyCustomer(customer);
            return null;
        });

        assertThat(storedCustomer(customer).emailVerified()).isTrue();
    }

    @Test
    void givenTheTokenNotUsed_whenTheEmailIsVerified_thenTheDatabaseRefusesIt() {
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.EMAIL_VERIFICATION));

        assertThatThrownBy(() -> subject.consuming(link, token -> {
            verifyCustomer(customer);
            return null;
        })).isInstanceOf(RuntimeException.class);

        assertThat(storedCustomer(customer).emailVerified()).isFalse();
    }

    @Test
    void givenATokenOfAnotherCustomer_whenItVerifiesThisOne_thenTheDatabaseRefusesIt() {
        User other = CustomerRegistrationsContractTest.customer("beto@example.com");
        arrange(other);
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.EMAIL_VERIFICATION));

        assertThatThrownBy(() -> subject.consuming(link, token -> {
            token.orElseThrow().use(UserTokenPurpose.EMAIL_VERIFICATION, NOW);
            subject.markUsed(token.orElseThrow());
            verifyCustomer(other);
            return null;
        })).isInstanceOf(RuntimeException.class);

        assertThat(storedCustomer(other).emailVerified()).isFalse();
    }

    @Test
    void givenAResetTokenUsedFirst_whenThePasswordIsReplacedAndTheEmailVerified_thenTheDatabaseAcceptsIt() {
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.PASSWORD_RESET));

        subject.consuming(link, token -> {
            token.orElseThrow().use(UserTokenPurpose.PASSWORD_RESET, NOW);
            subject.markUsed(token.orElseThrow());
            resetPassword(customer);
            return null;
        });

        User reset = storedCustomer(customer);
        assertThat(reset.passwordHash()).contains(NEW_HASH);
        assertThat(reset.emailVerified()).as("V31: the reset link proves the inbox too").isTrue();
    }

    @Test
    void givenTheResetTokenNotUsed_whenThePasswordIsReplaced_thenTheDatabaseRefusesIt() {
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.PASSWORD_RESET));

        assertThatThrownBy(() -> subject.consuming(link, token -> {
            resetPassword(customer);
            return null;
        })).isInstanceOf(RuntimeException.class);

        assertThat(storedCustomer(customer).passwordHash()).contains(customer.passwordHash().orElseThrow());
    }

    @Test
    void givenAVerificationToken_whenItReplacesThePassword_thenTheDatabaseRefusesIt() {
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.EMAIL_VERIFICATION));

        assertThatThrownBy(() -> subject.consuming(link, token -> {
            token.orElseThrow().use(UserTokenPurpose.EMAIL_VERIFICATION, NOW);
            subject.markUsed(token.orElseThrow());
            resetPassword(customer);
            return null;
        })).isInstanceOf(RuntimeException.class);

        assertThat(storedCustomer(customer).passwordHash()).contains(customer.passwordHash().orElseThrow());
    }

    @Test
    void givenAResetTokenOfAnotherCustomer_whenItReplacesThisOnesPassword_thenTheDatabaseRefusesIt() {
        User other = CustomerRegistrationsContractTest.customer("beto@example.com");
        arrange(other);
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.PASSWORD_RESET));

        assertThatThrownBy(() -> subject.consuming(link, token -> {
            token.orElseThrow().use(UserTokenPurpose.PASSWORD_RESET, NOW);
            subject.markUsed(token.orElseThrow());
            resetPassword(other);
            return null;
        })).isInstanceOf(RuntimeException.class);

        assertThat(storedCustomer(other).passwordHash()).contains(other.passwordHash().orElseThrow());
    }

    private void resetPassword(User target) {
        User loaded = users.findById(target.id()).orElseThrow();
        loaded.resetPassword(NEW_HASH, NOW);
        users.save(loaded);
        rows.flush();
    }

    /** Flushed at once, so the database's refusal surfaces inside the consumption. */
    private void verifyCustomer(User target) {
        User loaded = users.findById(target.id()).orElseThrow();
        loaded.verifyEmail(NOW);
        users.save(loaded);
        rows.flush();
    }

    private User storedCustomer(User target) {
        return CurrentActor.callAsSystem(() -> transactions.execute(status -> users.findById(target.id())))
            .orElseThrow();
    }
}
