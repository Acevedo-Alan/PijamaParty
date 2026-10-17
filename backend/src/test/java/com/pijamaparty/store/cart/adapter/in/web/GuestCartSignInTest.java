package com.pijamaparty.store.cart.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.application.MergeGuestCart;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** UC-19 (T-E4-04): which sign-ins join the guest cart, and as whom. */
class GuestCartSignInTest {

    private static final String TOKEN = "a".repeat(43);
    private static final UserId USER = UserId.of(UUID.randomUUID());

    private final List<Actor> merges = new ArrayList<>();
    private final List<Optional<Actor>> boundActors = new ArrayList<>();
    private final MergeGuestCart merge = command -> {
        merges.add(command.actor());
        boundActors.add(CurrentActor.get());
        return new MergeGuestCart.Result.Merged();
    };
    private final GuestCartSignIn listener = new GuestCartSignIn(new CartTokenCookie(), merge);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void givenACustomerWithACartCookie_whenSigningIn_thenItMergesAsThatCustomerCarryingTheTokenAndDropsTheCookie() {
        listener.signingIn(USER, Role.CUSTOMER, withCartCookie(), response);

        Actor expected = Actor.customer(USER, Optional.of(new CartToken(TOKEN)), Optional.empty());
        assertThat(merges).containsExactly(expected);
        assertThat(boundActors).as("the transaction's Row Level Security context").containsExactly(Optional.of(expected));
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith(CartTokenCookie.NAME + "=;")
            .contains("Max-Age=0", "Path=/", "Secure", "HttpOnly");
    }

    @Test
    void givenTheAdministratorOrNoCartCookie_whenSigningIn_thenNothingIsMergedAndNoCookieIsTouched() {
        listener.signingIn(USER, Role.ADMIN, withCartCookie(), response);
        listener.signingIn(USER, Role.CUSTOMER, new MockHttpServletRequest("POST", "/api/account/login"), response);

        assertThat(merges).isEmpty();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private static MockHttpServletRequest withCartCookie() {
        var request = new MockHttpServletRequest("POST", "/api/account/login");
        request.setCookies(new Cookie(CartTokenCookie.NAME, TOKEN));
        return request;
    }
}
