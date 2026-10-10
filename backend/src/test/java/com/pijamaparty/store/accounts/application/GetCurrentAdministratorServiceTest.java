package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.GetCurrentAdministrator.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCurrentAdministratorServiceTest {

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final GetCurrentAdministratorService service = new GetCurrentAdministratorService(users);

    @Test
    void givenTheSignedInAdministrator_whenAskingWhoItIs_thenItsViewIsReturned() {
        var admin = User.newAdministrator(UserId.of(UUID.randomUUID()), Email.parse("owner@example.com").orElseThrow(),
            "Owner", new InMemoryPasswordHasher().hash("a long enough password"), Instant.EPOCH);
        users.save(admin);

        assertThat(service.execute(Actor.admin(admin.id())))
            .isEqualTo(new Result.Found(new AdministratorView(admin.id(), "owner@example.com", "Owner")));
    }

    @Test
    void givenAnyOtherActorOrAnUnknownAdministrator_whenAsking_thenNobodyIsSignedIn() {
        assertThat(service.execute(Actor.anonymous(Optional.empty(), Optional.empty())))
            .isEqualTo(new Result.NotSignedIn());
        assertThat(service.execute(Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty())))
            .isEqualTo(new Result.NotSignedIn());
        assertThat(service.execute(Actor.admin(UserId.of(UUID.randomUUID())))).isEqualTo(new Result.NotSignedIn());
    }
}
