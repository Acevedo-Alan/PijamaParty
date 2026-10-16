package com.pijamaparty.store.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

    private static final UserId USER = UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final CartToken CART = new CartToken("cart-token");
    private static final TrackingToken TRACKING = new TrackingToken("tracking-token");
    private static final AccountToken ACCOUNT = new AccountToken("account-token");

    @Test
    void givenAGuestWithTokens_whenCreatingTheActor_thenItIsAnonymousWithoutUser() {
        Actor actor = Actor.anonymous(Optional.of(CART), Optional.of(TRACKING));

        assertThat(actor.role()).isEqualTo(Role.ANONYMOUS);
        assertThat(actor.userId()).isEmpty();
        assertThat(actor.cartToken()).contains(CART);
        assertThat(actor.trackingToken()).contains(TRACKING);
    }

    @Test
    void givenACustomer_whenCreatingTheActor_thenItCarriesTheUserId() {
        Actor actor = Actor.customer(USER, Optional.of(CART), Optional.empty());

        assertThat(actor.is(Role.CUSTOMER)).isTrue();
        assertThat(actor.userId()).contains(USER);
    }

    @Test
    void givenTheAdministratorAndTheSystem_whenCreatingTheActors_thenTheyCarryNoTokens() {
        assertThat(Actor.admin(USER).userId()).contains(USER);
        assertThat(Actor.system().userId()).isEmpty();
        assertThat(Actor.system().cartToken()).isEmpty();
        assertThat(Actor.system().accountToken()).isEmpty();
    }

    @Test
    void givenTheSystemConsumingAnAccountToken_whenCreatingTheActor_thenItCarriesOnlyThatToken() {
        Actor actor = Actor.systemConsuming(ACCOUNT);

        assertThat(actor.is(Role.SYSTEM)).isTrue();
        assertThat(actor.accountToken()).contains(ACCOUNT);
        assertThat(actor).isNotEqualTo(Actor.system());
    }

    @Test
    void givenACustomerOrAdminWithoutUserId_whenCreatingTheActor_thenItIsRejected() {
        assertThatThrownBy(() -> actor(Role.CUSTOMER, Optional.empty(), Optional.empty(), Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actor(Role.ADMIN, Optional.empty(), Optional.empty(), Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnAnonymousOrSystemActorWithUserId_whenCreatingTheActor_thenItIsRejected() {
        assertThatThrownBy(() -> actor(Role.ANONYMOUS, Optional.of(USER), Optional.empty(), Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actor(Role.SYSTEM, Optional.of(USER), Optional.empty(), Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenTheAdministratorWithATrackingToken_whenCreatingTheActor_thenItIsRejected() {
        assertThatThrownBy(() -> actor(Role.ADMIN, Optional.of(USER), Optional.of(TRACKING), Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnyoneButTheSystemWithAnAccountToken_whenCreatingTheActor_thenItIsRejected() {
        assertThatThrownBy(() -> actor(Role.ANONYMOUS, Optional.empty(), Optional.empty(), Optional.of(ACCOUNT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actor(Role.CUSTOMER, Optional.of(USER), Optional.empty(), Optional.of(ACCOUNT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actor(Role.ADMIN, Optional.of(USER), Optional.empty(), Optional.of(ACCOUNT)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnActorWithTokens_whenPrintingIt_thenTheTokensAreNotRevealed() {
        String printed = Actor.anonymous(Optional.of(CART), Optional.of(TRACKING)).toString();

        assertThat(printed).doesNotContain("cart-token", "tracking-token");
        assertThat(Actor.systemConsuming(ACCOUNT).toString()).doesNotContain("account-token");
    }

    private static Actor actor(Role role, Optional<UserId> userId, Optional<TrackingToken> trackingToken,
                               Optional<AccountToken> accountToken) {
        return new Actor(role, userId, Optional.empty(), trackingToken, accountToken);
    }
}
