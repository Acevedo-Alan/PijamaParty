package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadPendingLinks} and {@link SavePendingLinks} (T-E4-14): one external account waits per
 * customer, and a new request replaces it (whether it may is the use case's decision). Implementations provide the
 * customers.
 */
public abstract class PendingLinkStoreContractTest<T extends LoadPendingLinks & SavePendingLinks>
    extends ContractTest<T> {

    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");

    protected User ana;
    protected User beto;

    protected abstract void arrange(User user);

    @BeforeEach
    void twoCustomers() {
        ana = CustomerRegistrationsContractTest.customer("ana@example.com");
        beto = CustomerRegistrationsContractTest.customer("beto@example.com");
        arrange(ana);
        arrange(beto);
    }

    @Test
    void givenARequestedLink_whenFindingIt_thenItHasTheSameState() {
        var requested = new PendingLink(ana.id(), FACEBOOK, UserStoreContractTest.NOW);

        subject.request(requested);
        persistAndForget();

        assertThat(subject.find(ana.id())).contains(requested);
        assertThat(subject.find(beto.id())).isEmpty();
    }

    @Test
    void givenALinkWaiting_whenLockingItsCustomer_thenItReadsTheSameLink() {
        var requested = new PendingLink(ana.id(), FACEBOOK, UserStoreContractTest.NOW);
        subject.request(requested);
        persistAndForget();

        assertThat(subject.lockByCustomer(ana.id())).contains(requested);
        assertThat(subject.lockByCustomer(beto.id())).isEmpty();
    }

    @Test
    void givenALinkWaiting_whenAnotherIsRequested_thenItReplacesIt() {
        subject.request(new PendingLink(ana.id(), FACEBOOK, UserStoreContractTest.NOW));
        var later = new PendingLink(ana.id(), new ExternalAccount(ExternalProvider.FACEBOOK, "999"),
            UserStoreContractTest.NOW.plus(Duration.ofMinutes(3)));

        subject.request(later);
        persistAndForget();

        assertThat(subject.find(ana.id())).contains(later);
    }

    @Test
    void givenALinkWaiting_whenForgettingIt_thenNoneWaits() {
        subject.request(new PendingLink(ana.id(), FACEBOOK, UserStoreContractTest.NOW));
        subject.request(new PendingLink(beto.id(), FACEBOOK, UserStoreContractTest.NOW));

        subject.forget(ana.id());
        subject.forget(ana.id());
        persistAndForget();

        assertThat(subject.find(ana.id())).isEmpty();
        assertThat(subject.find(beto.id())).isPresent();
    }
}
