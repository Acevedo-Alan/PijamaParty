package com.pijamaparty.store.ordering.adapter.in.signin;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.LinkGuestOrders;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** UC-22 (T-E4-08): which sign-ins link guest orders, and as whom. */
class GuestOrdersSignInTest {

    private static final UserId USER = UserId.of(UUID.randomUUID());

    private final List<UserId> linked = new ArrayList<>();
    private final List<Optional<Actor>> boundActors = new ArrayList<>();
    private final LinkGuestOrders link = command -> {
        linked.add(command.customer());
        boundActors.add(CurrentActor.get());
        return new LinkGuestOrders.Result.Linked(1);
    };
    private final GuestOrdersSignIn listener = new GuestOrdersSignIn(link);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/account/login");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void givenACustomer_whenSigningIn_thenItsGuestOrdersAreLinkedAsTheSystem() {
        listener.signingIn(USER, Role.CUSTOMER, request, response);

        assertThat(linked).containsExactly(USER);
        assertThat(boundActors).as("the transaction's Row Level Security context")
            .containsExactly(Optional.of(Actor.system()));
        assertThat(CurrentActor.get()).as("the request's actor is restored").isEmpty();
    }

    @Test
    void givenTheAdministrator_whenSigningIn_thenNothingIsLinked() {
        listener.signingIn(USER, Role.ADMIN, request, response);

        assertThat(linked).isEmpty();
    }
}
