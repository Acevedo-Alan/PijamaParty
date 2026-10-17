package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link FindExternalIdentities} and {@link LinkExternalIdentities} (UC-27): an external account belongs
 * to one customer, and a customer links at most one account of each provider. Implementations provide the customers.
 */
public abstract class ExternalIdentityStoreContractTest<T extends FindExternalIdentities & LinkExternalIdentities>
    extends ContractTest<T> {

    protected static final ExternalAccount GOOGLE = new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");
    protected static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");

    protected User ana;
    protected User beto;

    protected abstract void arrange(User user);

    @BeforeEach
    void twoCustomers() {
        ana = ExternalCustomersContractTest.externalCustomer("ana@example.com", true);
        beto = ExternalCustomersContractTest.externalCustomer("beto@example.com", true);
        arrange(ana);
        arrange(beto);
    }

    protected static ExternalIdentity identity(User user, ExternalAccount account) {
        return ExternalIdentity.link(UUID.randomUUID(), user.id(), account, user.email(), UserStoreContractTest.NOW);
    }

    @Test
    void givenALinkedAccount_whenFindingIt_thenItHasTheSameState() {
        ExternalIdentity linked = identity(ana, GOOGLE);

        assertThat(subject.link(linked)).isTrue();
        persistAndForget();

        assertThat(subject.find(GOOGLE)).contains(linked);
        assertThat(subject.hasProvider(ana.id(), ExternalProvider.GOOGLE)).isTrue();
        assertThat(subject.hasProvider(ana.id(), ExternalProvider.FACEBOOK)).isFalse();
        assertThat(subject.hasProvider(beto.id(), ExternalProvider.GOOGLE)).isFalse();
    }

    @Test
    void givenAnAccountLinkedToOneCustomer_whenLinkingItToAnother_thenNothingIsLinked() {
        subject.link(identity(ana, GOOGLE));

        assertThat(subject.link(identity(beto, GOOGLE))).isFalse();

        assertThat(subject.find(GOOGLE).orElseThrow().userId()).isEqualTo(ana.id());
        assertThat(subject.hasProvider(beto.id(), ExternalProvider.GOOGLE)).isFalse();
    }

    @Test
    void givenACustomerWithAnAccountOfAProvider_whenLinkingAnotherOfIt_thenNothingIsLinked() {
        subject.link(identity(ana, GOOGLE));
        var anotherGoogle = new ExternalAccount(ExternalProvider.GOOGLE, "200000000000000000001");

        assertThat(subject.link(identity(ana, anotherGoogle))).isFalse();

        assertThat(subject.find(anotherGoogle)).isEmpty();
    }

    @Test
    void givenAnAccountOfEachProvider_whenLinkingBoth_thenBothAreLinked() {
        assertThat(subject.link(identity(ana, GOOGLE))).isTrue();
        assertThat(subject.link(identity(ana, FACEBOOK))).isTrue();

        assertThat(subject.hasProvider(ana.id(), ExternalProvider.GOOGLE)).isTrue();
        assertThat(subject.hasProvider(ana.id(), ExternalProvider.FACEBOOK)).isTrue();
    }

    @Test
    void givenNothingLinked_whenFindingAnAccount_thenThereIsNone() {
        assertThat(subject.find(FACEBOOK)).isEmpty();
    }
}
