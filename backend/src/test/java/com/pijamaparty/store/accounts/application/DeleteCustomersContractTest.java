package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link DeleteCustomers} (UC-28, T-E4-16): a customer's account goes with its external accounts, and no
 * other account is touched. Read back through {@link LoadSignInMethods}. Implementations provide the customers and
 * the links, which the system makes.
 */
public abstract class DeleteCustomersContractTest<T extends DeleteCustomers & LoadSignInMethods>
    extends ContractTest<T> {

    private static final ExternalAccount ANA_GOOGLE =
        new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");
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
        arrange(SignInMethodsStoreContractTest.identity(ana, ANA_GOOGLE));
        arrange(SignInMethodsStoreContractTest.identity(beto, BETO_GOOGLE));
    }

    @Test
    void givenACustomerWithALinkedAccount_whenDeleted_thenItAndItsLinkAreGoneAndTheOtherCustomerStays() {
        assertThat(subject.delete(ana.id())).isTrue();
        persistAndForget();

        assertThat(subject.findByCustomer(ana.id())).isEmpty();
        assertThat(subject.findByCustomer(beto.id()).orElseThrow().identities())
            .extracting(ExternalIdentity::account).containsExactly(BETO_GOOGLE);
    }

    @Test
    void givenACustomerAlreadyDeleted_whenDeletedAgain_thenNothingIsDeleted() {
        subject.delete(ana.id());
        persistAndForget();

        assertThat(subject.delete(ana.id())).isFalse();
    }
}
