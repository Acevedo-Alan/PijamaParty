package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link AddExternalCustomers} and {@link FindUserByEmail} (UC-27): a customer a provider creates, without
 * a password, and the user of an email. Implementations provide how to arrange a user created otherwise.
 */
public abstract class ExternalCustomersContractTest<T extends AddExternalCustomers & FindUserByEmail>
    extends ContractTest<T> {

    protected abstract void arrange(User user);

    public static User externalCustomer(String email, boolean verified) {
        return User.newExternalCustomer(UserId.of(UUID.randomUUID()), Email.parse(email).orElseThrow(), "Ana Pérez",
            verified, UserStoreContractTest.NOW);
    }

    @Test
    void givenANewEmail_whenAddingAVerifiedCustomer_thenItIsFoundByItsEmailWithoutAPassword() {
        User customer = externalCustomer("ana@example.com", true);

        assertThat(subject.add(customer)).isTrue();

        User found = subject.findByEmail(customer.email()).orElseThrow();
        assertThat(found).usingRecursiveComparison().isEqualTo(customer);
        assertThat(found.passwordHash()).isEmpty();
        assertThat(found.emailVerified()).isTrue();
    }

    @Test
    void givenANewEmail_whenAddingAnUnverifiedCustomer_thenItIsStoredUnverified() {
        User customer = externalCustomer("ana@example.com", false);

        assertThat(subject.add(customer)).isTrue();

        assertThat(subject.findByEmail(customer.email()).orElseThrow().emailVerified()).isFalse();
    }

    @Test
    void givenAnEmailAlreadyRegistered_whenAddingACustomerWithIt_thenNothingIsAdded() {
        User registered = CustomerRegistrationsContractTest.customer("ana@example.com");
        arrange(registered);

        assertThat(subject.add(externalCustomer("ana@example.com", true))).isFalse();

        assertThat(subject.findByEmail(registered.email())).get().extracting(User::id).isEqualTo(registered.id());
    }

    @Test
    void givenTheAdministratorsEmail_whenAddingACustomerWithIt_thenNothingIsAdded() {
        User administrator = UserStoreContractTest.administrator();
        arrange(administrator);

        assertThat(subject.add(externalCustomer(administrator.email().value(), true))).isFalse();

        assertThat(subject.findByEmail(administrator.email())).get().extracting(User::id)
            .isEqualTo(administrator.id());
    }

    @Test
    void givenAnUnknownEmail_whenFindingIt_thenThereIsNone() {
        assertThat(subject.findByEmail(Email.parse("nadie@example.com").orElseThrow())).isEmpty();
    }

    @Test
    void givenACustomerWithAPassword_whenAddingIt_thenItIsRefused() {
        User withPassword = CustomerRegistrationsContractTest.customer("ana@example.com");

        assertThatThrownBy(() -> subject.add(withPassword))
            .hasMessageContaining("a provider creates customers without a password");
    }
}
