package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

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

/** UC-28 (T-E4-16): only a provider the customer has linked can confirm the deletion of its account. */
class RequestReauthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final RequestReauthenticationService request =
        new RequestReauthenticationService(new InMemorySignInMethods(users, identities));

    @Test
    void givenACustomerWithGoogleLinked_whenItAsksToConfirmWithGoogle_thenItIsRequested() {
        User ana = customerWithGoogle();

        assertThat(request.execute(new RequestReauthentication.Command(actor(ana), ExternalProvider.GOOGLE)))
            .isEqualTo(new RequestReauthentication.Result.Requested());
    }

    @Test
    void givenACustomerWithoutFacebook_whenItAsksToConfirmWithFacebook_thenItIsNotLinked() {
        User ana = customerWithGoogle();

        assertThat(request.execute(new RequestReauthentication.Command(actor(ana), ExternalProvider.FACEBOOK)))
            .isEqualTo(new RequestReauthentication.Result.NotLinked());
    }

    @Test
    void givenAGuestOrADeletedAccount_whenAsking_thenItIsNotSignedIn() {
        var guest = Actor.anonymous(Optional.empty(), Optional.empty());
        var deleted = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(request.execute(new RequestReauthentication.Command(guest, ExternalProvider.GOOGLE)))
            .isEqualTo(new RequestReauthentication.Result.NotSignedIn());
        assertThat(request.execute(new RequestReauthentication.Command(deleted, ExternalProvider.GOOGLE)))
            .isEqualTo(new RequestReauthentication.Result.NotSignedIn());
    }

    private User customerWithGoogle() {
        User ana = User.newExternalCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana", true,
            NOW);
        users.save(ana);
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana.id(),
            new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321"), ana.email(), NOW));
        return ana;
    }

    private static Actor actor(User user) {
        return Actor.customer(user.id(), Optional.empty(), Optional.empty());
    }
}
