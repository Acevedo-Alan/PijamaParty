package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserNamesServiceTest {

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final UserNamesService service = new UserNamesService(users);

    @Test
    void givenAKnownUserAndAnUnknownOne_whenAskingForTheirNames_thenOnlyTheKnownOneIsNamed() {
        var admin = User.newAdministrator(UserId.of(UUID.randomUUID()), Email.parse("owner@example.com").orElseThrow(),
            "Dueña", new InMemoryPasswordHasher().hash("a long enough password"), Instant.EPOCH);
        users.save(admin);
        UserId unknown = UserId.of(UUID.randomUUID());

        assertThat(service.namesOf(List.of(admin.id(), unknown, admin.id()))).isEqualTo(Map.of(admin.id(), "Dueña"));
        assertThat(service.namesOf(List.of())).isEmpty();
    }
}
