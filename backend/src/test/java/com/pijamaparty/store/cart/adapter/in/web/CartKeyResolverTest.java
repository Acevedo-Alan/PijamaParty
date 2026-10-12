package com.pijamaparty.store.cart.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** T-E2-11: the cart key of the coupon policy (UC-10). */
class CartKeyResolverTest {

    private static final String TOKEN = "a".repeat(43);

    private final RateLimitKeyResolver resolver = new CartKeyResolver(new CartTokenCookie());

    @Test
    void givenTheSameCartToken_whenResolved_thenItIsTheSameCartWithoutTheTokenInClear() {
        String key = resolve(new Cookie(CartTokenCookie.NAME, TOKEN));

        assertThat(key).isEqualTo(resolve(new Cookie(CartTokenCookie.NAME, TOKEN))).doesNotContain(TOKEN).hasSize(64);
        assertThat(resolve(new Cookie(CartTokenCookie.NAME, "b".repeat(43)))).isNotEqualTo(key);
    }

    @Test
    void givenNoWellFormedCartCookie_whenResolved_thenThereIsNoCart() {
        assertThat(resolver.resolve(new MockHttpServletRequest("POST", "/api/cart/coupon"))).isEmpty();
        assertThat(resolveOptional(new Cookie(CartTokenCookie.NAME, "too-short"))).isEmpty();
        assertThat(resolveOptional(new Cookie("cart_token", TOKEN))).as("without the __Host- prefix").isEmpty();
    }

    private String resolve(Cookie cookie) {
        return resolveOptional(cookie).orElseThrow();
    }

    private Optional<String> resolveOptional(Cookie cookie) {
        var request = new MockHttpServletRequest("POST", "/api/cart/coupon");
        request.setCookies(cookie);
        return resolver.resolve(request);
    }
}
