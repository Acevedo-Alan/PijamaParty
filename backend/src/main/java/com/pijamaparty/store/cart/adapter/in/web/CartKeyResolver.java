package com.pijamaparty.store.cart.adapter.in.web;

import com.pijamaparty.store.platform.domain.AccessToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The cart of a request, for the coupon policy of UC-10 (T-E2-11, RNF-22 "Carrito + IP"): the guest's well-formed
 * {@code __Host-cart_token} cookie, read as {@link CartTokenCookie} reads it. The key is the token's SHA-256, so not
 * even the limiter's memory holds the token. Without a well-formed cookie there is no cart and the policy does not
 * apply; the cart-changes policy per IP is the baseline. A customer's cart (by its session) joins this key in E4.
 */
@Component
class CartKeyResolver implements RateLimitKeyResolver {

    private final CartTokenCookie cookie;

    CartKeyResolver(CartTokenCookie cookie) {
        this.cookie = cookie;
    }

    @Override
    public String name() {
        return "cart";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        return cookie.resolve(request).flatMap(Actor::cartToken).map(AccessToken::hash);
    }
}
