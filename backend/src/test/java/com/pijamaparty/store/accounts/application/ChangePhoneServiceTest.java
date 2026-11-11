package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-40 (T-E4-18): the signed-in customer adds or changes its phone from "Datos y acceso". */
class ChangePhoneServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemorySignInMethods methods = new InMemorySignInMethods(users, new InMemoryExternalIdentities());
    private final InMemoryCustomerAccountChanges changes = new InMemoryCustomerAccountChanges();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final ChangePhoneService changePhone = new ChangePhoneService(changes, methods, users, users,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenACustomerWithoutAPhone_whenItSaves342_400_0000_thenItsAccountHasTheDigits() {
        User ana = customer();

        var result = changePhone.execute(new ChangePhone.Command(actor(ana), "342 400-0000"));

        assertThat(result).isEqualTo(new ChangePhone.Result.Changed("3424000000"));
        assertThat(users.findById(ana.id()).orElseThrow().phone()).contains("3424000000");
    }

    @Test
    void givenACustomer_whenItSaves123_thenItIsAnInvalidPhoneAndNothingChanges() {
        User ana = customer();
        ana.rememberPhone("3425551234", NOW);
        users.save(ana);

        var result = changePhone.execute(new ChangePhone.Command(actor(ana), "123"));

        assertThat(result).isEqualTo(new ChangePhone.Result.InvalidPhone());
        assertThat(users.findById(ana.id()).orElseThrow().phone()).contains("3425551234");
    }

    @Test
    void givenAVisitorWhoIsNotSignedIn_whenItSendsAPhone_thenItIsNotSignedIn() {
        var visitor = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThat(changePhone.execute(new ChangePhone.Command(visitor, "3424000000")))
            .isEqualTo(new ChangePhone.Result.NotSignedIn());
    }

    @Test
    void givenACustomerWhoseAccountNoLongerExists_whenItSendsAPhone_thenItIsNotSignedIn() {
        var gone = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(changePhone.execute(new ChangePhone.Command(gone, "3424000000")))
            .isEqualTo(new ChangePhone.Result.NotSignedIn());
    }

    @Test
    void givenACommandAndItsResult_whenPrinted_thenThePhoneDoesNotShow() {
        User ana = customer();

        var result = changePhone.execute(new ChangePhone.Command(actor(ana), "3424000000"));

        assertThat(result.toString()).doesNotContain("3424000000");
        assertThat(new ChangePhone.Command(actor(ana), "3424000000").toString()).doesNotContain("3424000000");
    }

    private User customer() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana",
            passwords.hash("una clave"), NOW);
        users.save(ana);
        return ana;
    }

    private static Actor actor(User user) {
        return Actor.customer(user.id(), Optional.empty(), Optional.empty());
    }
}
