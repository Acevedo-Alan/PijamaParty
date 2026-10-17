package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract of {@link PasswordRecoveries} (UC-19, T-E4-03). Implementations provide how a stored user is arranged. */
public abstract class PasswordRecoveriesContractTest<T extends PasswordRecoveries> extends ContractTest<T> {

    protected abstract void arrange(User user);

    @Test
    void givenACustomer_whenRequestingWithItsEmail_thenTheRequestGetsThatCustomer() {
        User customer = CustomerRegistrationsContractTest.customer("ana@example.com");
        arrange(customer);
        List<Optional<User>> seen = new ArrayList<>();

        subject.requesting(new Email("ana@example.com"), seen::add);

        assertThat(seen).singleElement().satisfies(found ->
            assertThat(found).get().usingRecursiveComparison().isEqualTo(customer));
    }

    @Test
    void givenAnUnknownEmail_whenRequesting_thenTheRequestGetsNobody() {
        arrange(CustomerRegistrationsContractTest.customer("ana@example.com"));
        List<Optional<User>> seen = new ArrayList<>();

        subject.requesting(new Email("beto@example.com"), seen::add);

        assertThat(seen).containsExactly(Optional.empty());
    }
}
