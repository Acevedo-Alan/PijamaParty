package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.EmailVerificationRequested;
import com.pijamaparty.store.accounts.application.RegisterCustomer.Command;
import com.pijamaparty.store.accounts.application.RegisterCustomer.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-19 (T-E4-02): a visitor registers with email, password and name. */
class RegisterCustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");
    private static final UserId FIRST_ID = UserId.of(InMemoryIdGenerator.nth(1));

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final InMemoryAccountEvents events = new InMemoryAccountEvents();
    private final RegisterCustomerService service = new RegisterCustomerService(
        new InMemoryCustomerRegistrations(users), passwords, events, new InMemoryIdGenerator(),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenANewEmail_whenRegistering_thenAnUnverifiedCustomerIsCreatedAndItsVerificationEmailRequested() {
        Result result = service.execute(new Command(" Ana@Example.com ", "ocho1234", "  Ana Pérez "));

        assertThat(result).isEqualTo(new Result.Registered(FIRST_ID));
        User customer = users.findById(FIRST_ID).orElseThrow();
        assertThat(customer.email()).isEqualTo(new Email("ana@example.com"));
        assertThat(customer.name()).isEqualTo("Ana Pérez");
        assertThat(customer.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(customer.emailVerified()).isFalse();
        assertThat(customer.passwordHash()).contains(passwords.hash("ocho1234"));
        assertThat(customer.createdAt()).isEqualTo(NOW);
        assertThat(events.published()).containsExactly(new EmailVerificationRequested(FIRST_ID.value()));
    }

    @Test
    void givenAnEmailAlreadyRegistered_whenRegisteringIt_thenItIsRefusedWithoutTouchingThatAccount() {
        service.execute(new Command("ana@example.com", "ocho1234", "Ana"));

        Result again = service.execute(new Command("ANA@example.com", "otra-clave", "Otra Ana"));

        assertThat(again).isEqualTo(new Result.EmailAlreadyRegistered());
        assertThat(users.findById(FIRST_ID).orElseThrow().name()).isEqualTo("Ana");
        assertThat(events.published()).hasSize(1);
    }

    @Test
    void givenTheAdministratorsEmail_whenACustomerRegistersIt_thenItIsRefused() {
        users.save(User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"), "Owner",
            passwords.hash("correct horse battery"), NOW));

        assertThat(service.execute(new Command("owner@example.com", "ocho1234", "Ana")))
            .isEqualTo(new Result.EmailAlreadyRegistered());
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenInvalidFields_whenRegistering_thenEachIsRefusedAndNothingIsCreated() {
        assertThat(service.execute(new Command("not an email", "ocho1234", "Ana")))
            .isEqualTo(new Result.InvalidEmail());
        assertThat(service.execute(new Command(null, "ocho1234", "Ana"))).isEqualTo(new Result.InvalidEmail());
        assertThat(service.execute(new Command("ana@example.com", "ocho1234", "   ")))
            .isEqualTo(new Result.InvalidName());
        assertThat(service.execute(new Command("ana@example.com", "ocho1234", "x".repeat(101))))
            .isEqualTo(new Result.InvalidName());
        assertThat(service.execute(new Command("ana@example.com", "siete77", "Ana")))
            .isEqualTo(new Result.InvalidPassword(PasswordRules.Violation.TOO_SHORT));
        assertThat(service.execute(new Command("ana@example.com", "x".repeat(73), "Ana")))
            .isEqualTo(new Result.InvalidPassword(PasswordRules.Violation.TOO_LONG));

        assertThat(users.findById(FIRST_ID)).isEmpty();
        assertThat(events.published()).isEmpty();
    }
}
