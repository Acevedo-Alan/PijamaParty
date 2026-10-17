package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerRegistrations} (UC-19). Implementations provide how to arrange and read back a stored
 * user.
 */
public abstract class CustomerRegistrationsContractTest<T extends CustomerRegistrations> extends ContractTest<T> {

    protected abstract void arrange(User user);

    protected abstract Optional<User> stored(UserId id);

    public static User customer(String email) {
        return User.newCustomer(UserId.of(UUID.randomUUID()), Email.parse(email).orElseThrow(), "Ana Pérez",
            UserStoreContractTest.HASH, UserStoreContractTest.NOW);
    }

    @Test
    void givenANewEmail_whenRegistering_thenTheCustomerIsStoredUnverifiedAndTheFollowUpRuns() {
        User customer = customer("ana@example.com");
        var followUps = new AtomicInteger();

        assertThat(subject.register(customer, followUps::incrementAndGet)).isTrue();

        assertThat(stored(customer.id())).get().usingRecursiveComparison().isEqualTo(customer);
        assertThat(stored(customer.id()).orElseThrow().emailVerified()).isFalse();
        assertThat(followUps).hasValue(1);
    }

    @Test
    void givenAnEmailAlreadyRegistered_whenRegisteringIt_thenNothingIsStoredAndNothingFollows() {
        User first = customer("ana@example.com");
        subject.register(first, () -> { });
        User second = customer("ana@example.com");
        var followUps = new AtomicInteger();

        assertThat(subject.register(second, followUps::incrementAndGet)).isFalse();

        assertThat(stored(second.id())).isEmpty();
        assertThat(followUps).hasValue(0);
    }

    @Test
    void givenTheAdministratorsEmail_whenACustomerRegistersIt_thenNothingIsStored() {
        User administrator = UserStoreContractTest.administrator();
        arrange(administrator);
        User customer = customer(administrator.email().value());

        assertThat(subject.register(customer, () -> { })).isFalse();

        assertThat(stored(customer.id())).isEmpty();
    }

    @Test
    void givenAVerifiedUser_whenRegisteringIt_thenItIsRefused() {
        User verified = customer("ana@example.com");
        verified.verifyEmail(UserStoreContractTest.NOW);

        assertThatThrownBy(() -> subject.register(verified, () -> { }))
            .hasMessageContaining("only unverified customers with a password register");
    }
}
