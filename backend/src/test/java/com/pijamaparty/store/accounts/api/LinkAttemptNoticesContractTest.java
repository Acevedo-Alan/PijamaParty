package com.pijamaparty.store.accounts.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.LinkAttemptNotices.Recipient;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LinkAttemptNotices} (T-E4-14), for the real service and the fake the notice composer is tested
 * with. Implementations provide the users.
 */
public abstract class LinkAttemptNoticesContractTest<T extends LinkAttemptNotices> extends ContractTest<T> {

    /** A customer with this email and name. */
    protected abstract UUID customer(String email, String name);

    /** The administrator, with this email. */
    protected abstract UUID administrator(String email);

    @Test
    void givenACustomer_whenAskingForTheRecipient_thenItIsItsEmailAndName() {
        UUID ana = customer("ana@example.com", "Ana Pérez");

        assertThat(subject.recipientOf(ana)).contains(new Recipient("ana@example.com", "Ana Pérez"));
    }

    @Test
    void givenNoSuchUser_whenAskingForTheRecipient_thenThereIsNone() {
        assertThat(subject.recipientOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    void givenTheAdministrator_whenAskingForTheRecipient_thenThereIsNone() {
        assertThat(subject.recipientOf(administrator("owner@example.com"))).isEmpty();
    }

    @Test
    void givenARecipient_whenPrinted_thenItRevealsNoPersonalData() {
        assertThat(new Recipient("ana@example.com", "Ana").toString()).doesNotContain("ana", "Ana");
    }
}
