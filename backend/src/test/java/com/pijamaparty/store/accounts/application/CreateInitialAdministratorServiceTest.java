package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.CreateInitialAdministrator.Command;
import com.pijamaparty.store.accounts.application.CreateInitialAdministrator.Result;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateInitialAdministratorServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final CreateInitialAdministratorService service = new CreateInitialAdministratorService(users, users,
        passwords, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenNoAdministrator_whenTheSettingsAreValid_thenTheAdministratorIsCreatedWithAHashedPassword() {
        Result result = service.execute(new Command(" Owner@Example.com ", " Dueña ", "a long enough password"));

        assertThat(result).isEqualTo(new Result.Created(UserId.of(ID)));
        User created = users.findById(UserId.of(ID)).orElseThrow();
        assertThat(created.role()).isEqualTo(UserRole.ADMIN);
        assertThat(created.email().value()).isEqualTo("owner@example.com");
        assertThat(created.name()).isEqualTo("Dueña");
        assertThat(created.passwordHash()).contains(passwords.hash("a long enough password"));
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void givenAnExistingAdministrator_whenTheSettingsAreApplied_thenNothingChanges() {
        service.execute(new Command("owner@example.com", "Owner", "a long enough password"));

        Result again = new CreateInitialAdministratorService(users, users, passwords, UUID::randomUUID,
            Clock.fixed(NOW, ZoneOffset.UTC)).execute(new Command("other@example.com", "Other", "another password!"));

        assertThat(again).isEqualTo(new Result.AlreadyExists());
        assertThat(users.findById(UserId.of(ID)).orElseThrow().passwordHash())
            .contains(passwords.hash("a long enough password"));
    }

    @Test
    void givenInvalidSettings_whenApplied_thenNothingIsCreatedAndTheReasonIsReturned() {
        assertThat(service.execute(new Command("not an email", "Owner", "a long enough password")))
            .isEqualTo(new Result.InvalidEmail());
        assertThat(service.execute(new Command("owner@example.com", "  ", "a long enough password")))
            .isEqualTo(new Result.InvalidName());
        assertThat(service.execute(new Command("owner@example.com", "Owner", "too short")))
            .isEqualTo(new Result.InvalidPassword(PasswordRules.Violation.TOO_SHORT));
        assertThat(service.execute(new Command("owner@example.com", "Owner", "x".repeat(73))))
            .isEqualTo(new Result.InvalidPassword(PasswordRules.Violation.TOO_LONG));
        assertThat(users.administratorExists()).isFalse();
    }
}
