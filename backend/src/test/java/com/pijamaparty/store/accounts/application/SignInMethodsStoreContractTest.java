package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadSignInMethods} and {@link UnlinkExternalIdentities} (UC-27, T-E4-15): a customer's password
 * and its own external accounts, and unlinking one of them. Implementations provide the customers and the links,
 * which the system makes.
 */
public abstract class SignInMethodsStoreContractTest<T extends LoadSignInMethods & UnlinkExternalIdentities>
    extends ContractTest<T> {

    protected static final ExternalAccount GOOGLE = new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");
    protected static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");
    private static final ExternalAccount BETO_GOOGLE =
        new ExternalAccount(ExternalProvider.GOOGLE, "200000000000000000001");

    protected User ana;
    protected User beto;

    protected abstract void arrange(User user);

    protected abstract void arrange(ExternalIdentity identity);

    @BeforeEach
    void twoCustomers() {
        ana = ExternalCustomersContractTest.externalCustomer("ana@example.com", true);
        beto = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("beto@example.com"), "Beto",
            UserStoreContractTest.HASH, UserStoreContractTest.NOW);
        arrange(ana);
        arrange(beto);
    }

    protected static ExternalIdentity identity(User user, ExternalAccount account) {
        return ExternalIdentity.link(UUID.randomUUID(), user.id(), account, user.email(), UserStoreContractTest.NOW);
    }

    @Test
    void givenACustomerWithItsAccountsLinked_whenLoadingItsMethods_thenItHasOnlyItsOwnGoogleFirst() {
        ExternalIdentity facebook = identity(ana, FACEBOOK);
        ExternalIdentity google = identity(ana, GOOGLE);
        arrange(facebook);
        arrange(google);
        arrange(identity(beto, BETO_GOOGLE));
        persistAndForget();

        SignInMethods methods = subject.findByCustomer(ana.id()).orElseThrow();

        assertThat(methods.customer()).isEqualTo(ana.id());
        assertThat(methods.hasPassword()).isFalse();
        assertThat(methods.identities()).containsExactly(google, facebook);
    }

    @Test
    void givenACustomerWithAPasswordAndNoLinks_whenLockingItsMethods_thenItHasThePasswordOnly() {
        SignInMethods methods = subject.lockByCustomer(beto.id()).orElseThrow();

        assertThat(methods.hasPassword()).isTrue();
        assertThat(methods.identities()).isEmpty();
    }

    @Test
    void givenNoAccount_whenLoadingItsMethods_thenNothingIsFound() {
        UserId unknown = UserId.of(UUID.randomUUID());

        assertThat(subject.findByCustomer(unknown)).isEmpty();
        assertThat(subject.lockByCustomer(unknown)).isEmpty();
    }

    @Test
    void givenTwoLinkedAccounts_whenUnlinkingOne_thenOnlyTheOtherRemains() {
        ExternalIdentity google = identity(ana, GOOGLE);
        ExternalIdentity facebook = identity(ana, FACEBOOK);
        arrange(google);
        arrange(facebook);

        subject.unlink(facebook);
        persistAndForget();

        assertThat(subject.findByCustomer(ana.id()).orElseThrow().identities()).containsExactly(google);
    }
}
