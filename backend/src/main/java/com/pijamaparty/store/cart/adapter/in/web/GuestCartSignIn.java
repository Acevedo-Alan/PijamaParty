package com.pijamaparty.store.cart.adapter.in.web;

import com.pijamaparty.store.cart.application.MergeGuestCart;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.security.SignInListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * UC-19 (T-E4-04): a customer who signs in from a browser with a guest cart gets that cart joined to its own, as the
 * customer carrying the browser's cart token, and the browser drops the token: signing out starts a new guest cart.
 * The administrator's sign-in leaves the guest cart alone.
 */
@Component
class GuestCartSignIn implements SignInListener {

    private final CartTokenCookie cookie;
    private final MergeGuestCart merge;

    GuestCartSignIn(CartTokenCookie cookie, MergeGuestCart merge) {
        this.cookie = cookie;
        this.merge = merge;
    }

    @Override
    public void signingIn(UserId userId, Role role, HttpServletRequest request, HttpServletResponse response) {
        if (role != Role.CUSTOMER) {
            return;
        }
        Optional<CartToken> token = cookie.resolve(request).flatMap(Actor::cartToken);
        if (token.isEmpty()) {
            return;
        }
        Actor customer = Actor.customer(userId, token, Optional.empty());
        CurrentActor.callAs(customer, () -> merge.execute(new MergeGuestCart.Command(customer)));
        response.addHeader(HttpHeaders.SET_COOKIE, CartTokenCookie.expiredCookie());
    }
}
