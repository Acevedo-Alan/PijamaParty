package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link VerifiedCustomerEmails} (UC-22, T-E4-08): only a customer's email once verified. */
public abstract class VerifiedCustomerEmailsContractTest<T extends VerifiedCustomerEmails> extends ContractTest<T> {

    /** A customer with {@code email}, verified or not. */
    protected abstract UserId givenCustomer(String email, boolean verified);

    @Test
    void givenACustomerWithItsEmailVerified_whenAsked_thenItsEmailIsAnswered() {
        UserId ana = givenCustomer("ana@example.com", true);

        assertThat(subject.of(ana)).contains("ana@example.com");
    }

    @Test
    void givenACustomerWithItsEmailNotVerified_whenAsked_thenThereIsNone() {
        UserId ana = givenCustomer("ana@example.com", false);

        assertThat(subject.of(ana)).isEmpty();
    }

    @Test
    void givenAnUnknownUser_whenAsked_thenThereIsNone() {
        assertThat(subject.of(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
