package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadUser} and {@link SaveUser}. Subjects run as the system, which may create the administrator. */
public abstract class UserStoreContractTest<T extends LoadUser & SaveUser> extends ContractTest<T> {

    /** Microseconds: what PostgreSQL keeps of an instant. */
    public static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    /** Well-formed for the database check (V6); never a real password. */
    public static final PasswordHash HASH =
        new PasswordHash("{bcrypt}$2a$12$" + "a".repeat(53));

    public static User administrator() {
        return User.newAdministrator(UserId.of(UUID.randomUUID()), Email.parse("owner@example.com").orElseThrow(),
            "Owner", HASH, NOW);
    }

    @Test
    void givenASavedAdministrator_whenLoadingIt_thenItHasTheSameState() {
        User saved = administrator();
        subject.save(saved);
        persistAndForget();

        User loaded = subject.findById(saved.id()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
    }

    @Test
    void givenNoAdministrator_whenOneIsSaved_thenAnAdministratorExists() {
        assertThat(subject.administratorExists()).isFalse();

        subject.save(administrator());
        persistAndForget();

        assertThat(subject.administratorExists()).isTrue();
    }

    @Test
    void givenAnUnknownId_whenLoading_thenItIsEmpty() {
        assertThat(subject.findById(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
