package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.PasswordResetRequested;
import com.pijamaparty.store.accounts.application.RequestPasswordReset.Command;
import com.pijamaparty.store.accounts.application.RequestPasswordReset.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-19 (T-E4-03): someone asks for a password reset link. */
class RequestPasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryAccountEvents events = new InMemoryAccountEvents();
    private final RequestPasswordResetService service =
        new RequestPasswordResetService(new InMemoryPasswordRecoveries(users), events);

    @Test
    void givenACustomer_whenItAsksWithItsEmailAsTyped_thenItsResetEmailIsRequested() {
        User customer = customer("ana@example.com");

        assertThat(service.execute(new Command(" ANA@example.com "))).isEqualTo(new Result.Requested());

        assertThat(events.published()).containsExactly(new PasswordResetRequested(customer.id().value()));
    }

    @Test
    void givenAnUnverifiedCustomerOrOneWithoutPassword_whenItAsks_thenItsResetEmailIsRequestedToo() {
        User unverified = customer("ana@example.com");
        User withoutPassword = User.restore(UserId.of(UUID.randomUUID()), new Email("beto@example.com"), true, null,
            "Beto", UserRole.CUSTOMER, 0, null, NOW, NOW);
        users.save(withoutPassword);

        service.execute(new Command("ana@example.com"));
        service.execute(new Command("beto@example.com"));

        assertThat(events.published()).containsExactly(new PasswordResetRequested(unverified.id().value()),
            new PasswordResetRequested(withoutPassword.id().value()));
    }

    @Test
    void givenAnUnknownEmailAMalformedOneOrTheAdministrators_whenAsking_thenTheAnswerIsTheSameAndNothingIsSent() {
        users.save(User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"), "Owner",
            new InMemoryPasswordHasher().hash("correct horse battery"), NOW));

        for (String email : new String[] {"nadie@example.com", "not an email", null, "owner@example.com"}) {
            assertThat(service.execute(new Command(email))).isEqualTo(new Result.Requested());
        }

        assertThat(events.published()).isEmpty();
    }

    private User customer(String email) {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email(email), "Ana Pérez",
            new InMemoryPasswordHasher().hash("ocho1234"), NOW);
        users.save(customer);
        return customer;
    }
}
