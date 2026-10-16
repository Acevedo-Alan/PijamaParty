package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.GetCurrentCustomer.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCurrentCustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final GetCurrentCustomerService service = new GetCurrentCustomerService(users);

    @Test
    void givenASignedInCustomer_whenAskingWhoItIs_thenItGetsItsIdEmailAndName() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana",
            new InMemoryPasswordHasher().hash("ocho1234"), NOW);
        users.save(customer);

        assertThat(service.execute(Actor.customer(customer.id(), Optional.empty(), Optional.empty())))
            .isEqualTo(new Result.Found(new CustomerView(customer.id(), "ana@example.com", "Ana")));
    }

    @Test
    void givenAGuestTheAdministratorOrADeletedCustomer_whenAskingWhoItIs_thenItIsNotSignedIn() {
        User administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"),
            "Owner", new InMemoryPasswordHasher().hash("correct horse battery"), NOW);
        users.save(administrator);

        assertThat(service.execute(Actor.anonymous(Optional.empty(), Optional.empty())))
            .isEqualTo(new Result.NotSignedIn());
        assertThat(service.execute(Actor.admin(administrator.id()))).isEqualTo(new Result.NotSignedIn());
        assertThat(service.execute(Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty())))
            .isEqualTo(new Result.NotSignedIn());
    }
}
