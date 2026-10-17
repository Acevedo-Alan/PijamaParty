package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.CustomerAccountDeleted;
import com.pijamaparty.store.accounts.application.DeleteCustomerAccount.Confirmation;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-28 (T-E4-16): the signed-in customer deletes its account, confirmed with its password or a provider. */
class DeleteCustomerAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount GOOGLE = new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemorySignInMethods methods = new InMemorySignInMethods(users, identities);
    private final InMemoryCustomerAccountChanges changes = new InMemoryCustomerAccountChanges();
    private final InMemoryAccountEvents events = new InMemoryAccountEvents();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final InMemoryUserSessions sessions = new InMemoryUserSessions();
    private final DeleteCustomerAccountService delete = new DeleteCustomerAccountService(changes, methods, users,
        methods, events, passwords, sessions);

    @Test
    void givenTheRightPassword_whenDeleting_thenTheAccountAndItsLinksAreGoneTheEventIsPublishedAndSessionsEnd() {
        User ana = customerWithPassword("una clave larga");
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana.id(), GOOGLE, ana.email(), NOW));

        var result = delete.execute(command(ana, new Confirmation.Password("una clave larga")));

        assertThat(result).isEqualTo(new DeleteCustomerAccount.Result.Deleted());
        assertThat(users.findById(ana.id())).isEmpty();
        assertThat(identities.all()).isEmpty();
        assertThat(events.published()).containsExactly(new CustomerAccountDeleted(ana.id().value()));
        assertThat(sessions.ended()).containsExactly(ana.id());
    }

    @Test
    void givenAWrongPassword_whenDeleting_thenItIsRefusedNothingChangesAndNoAttemptCounts() {
        User ana = customerWithPassword("una clave larga");

        var result = delete.execute(command(ana, new Confirmation.Password("otra clave")));

        assertThat(result).isEqualTo(new DeleteCustomerAccount.Result.WrongPassword());
        assertThat(users.findById(ana.id()).orElseThrow().failedSignInAttempts()).isZero();
        assertThat(events.published()).isEmpty();
        assertThat(sessions.ended()).isEmpty();
    }

    @Test
    void givenAnAccountWithoutPassword_whenDeletingWithOne_thenItIsAWrongPassword() {
        User ana = customerWithoutPassword();

        assertThat(delete.execute(command(ana, new Confirmation.Password("una clave larga"))))
            .isEqualTo(new DeleteCustomerAccount.Result.WrongPassword());
        assertThat(users.findById(ana.id())).isPresent();
    }

    @Test
    void givenARecentReauthentication_whenDeletingWithoutPassword_thenItIsDeletedInOneTransaction() {
        User ana = customerWithoutPassword();

        var result = delete.execute(command(ana, new Confirmation.Reauthenticated()));

        assertThat(result).isEqualTo(new DeleteCustomerAccount.Result.Deleted());
        assertThat(users.findById(ana.id())).isEmpty();
        assertThat(changes.transactions()).isOne();
    }

    @Test
    void givenNeitherPasswordNorReauthentication_whenDeleting_thenItIsRefusedBeforeAnyTransaction() {
        User ana = customerWithPassword("una clave larga");

        var result = delete.execute(command(ana, new Confirmation.None()));

        assertThat(result).isEqualTo(new DeleteCustomerAccount.Result.ConfirmationRequired());
        assertThat(changes.transactions()).isZero();
        assertThat(users.findById(ana.id())).isPresent();
    }

    @Test
    void givenTheAdministratorsId_whenDeletingAsACustomer_thenItIsNotSignedInAndStays() {
        User owner = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"), "Owner",
            passwords.hash("una clave muy larga"), NOW);
        users.save(owner);
        Actor asCustomer = Actor.customer(owner.id(), Optional.empty(), Optional.empty());

        var result = delete.execute(new DeleteCustomerAccount.Command(asCustomer,
            new Confirmation.Password("una clave muy larga")));

        assertThat(result).isEqualTo(new DeleteCustomerAccount.Result.NotSignedIn());
        assertThat(users.findById(owner.id())).isPresent();
    }

    @Test
    void givenAGuest_whenDeleting_thenItIsNotSignedIn() {
        var guest = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThat(delete.execute(new DeleteCustomerAccount.Command(guest, new Confirmation.Reauthenticated())))
            .isEqualTo(new DeleteCustomerAccount.Result.NotSignedIn());
    }

    @Test
    void givenAnAccountAlreadyDeleted_whenDeletingAgain_thenItIsNotSignedInAndNothingMoreIsPublished() {
        User ana = customerWithoutPassword();
        delete.execute(command(ana, new Confirmation.Reauthenticated()));

        var again = delete.execute(command(ana, new Confirmation.Reauthenticated()));

        assertThat(again).isEqualTo(new DeleteCustomerAccount.Result.NotSignedIn());
        assertThat(events.published()).hasSize(1);
    }

    @Test
    void givenTheAccountRowVanishesBeforeTheDelete_whenDeleting_thenItIsNotSignedInAndNothingIsPublished() {
        User ana = customerWithoutPassword();
        var deleteFindingNone = new DeleteCustomerAccountService(changes, methods, users, customer -> false, events,
            passwords, sessions);

        var result = deleteFindingNone.execute(command(ana, new Confirmation.Reauthenticated()));

        assertThat(result).isEqualTo(new DeleteCustomerAccount.Result.NotSignedIn());
        assertThat(events.published()).isEmpty();
        assertThat(sessions.ended()).isEmpty();
    }

    private User customerWithPassword(String password) {
        User user = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana",
            passwords.hash(password), NOW);
        users.save(user);
        return user;
    }

    private User customerWithoutPassword() {
        User user = User.newExternalCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana", true,
            NOW);
        users.save(user);
        return user;
    }

    private static DeleteCustomerAccount.Command command(User user, Confirmation confirmation) {
        return new DeleteCustomerAccount.Command(Actor.customer(user.id(), Optional.empty(), Optional.empty()),
            confirmation);
    }
}
