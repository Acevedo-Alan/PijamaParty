package com.pijamaparty.store.cart.adapter.in.web;

import com.pijamaparty.store.cart.application.RemoveIdleCarts;
import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.security.ActorResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * T-E2-04: the {@code __Host-cart_token} cookie that identifies a guest's cart. A request with a well-formed cookie
 * acts as a guest with that token, after the session resolver, so the administrator stays the administrator. A
 * malformed cookie is ignored: it could never have been issued.
 */
@Component
@Order(1)
final class CartTokenCookie implements ActorResolver {

    /**
     * The {@code __Host-} prefix makes browsers accept it only from this host over HTTPS, with {@code Path=/} and no
     * {@code Domain}: a sibling subdomain or a plain-HTTP response cannot plant a token in the shopper's browser
     * (cookie fixation), and no other cookie of that name can shadow it.
     */
    static final String NAME = "__Host-cart_token";

    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{" + TokenGenerator.LENGTH + "}");

    @Override
    public Optional<Actor> resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> NAME.equals(cookie.getName()) && WELL_FORMED.matcher(cookie.getValue()).matches())
            .findFirst()
            .map(cookie -> Actor.anonymous(Optional.of(new CartToken(cookie.getValue())), Optional.empty()));
    }

    /** {@code Set-Cookie} value: it lives as long as an idle cart. */
    static String setCookie(CartToken token) {
        return ResponseCookie.from(NAME, token.value())
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(RemoveIdleCarts.IDLE_PERIOD)
            .build()
            .toString();
    }

    /** {@code Set-Cookie} value that makes the browser drop the token: its cart joined the customer's (T-E4-04). */
    static String expiredCookie() {
        return ResponseCookie.from(NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build()
            .toString();
    }
}
