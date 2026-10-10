package com.pijamaparty.store.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitProperties.Policy;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimiterTest {

    private static final RateLimitKeyResolver IP = new ClientIpKeyResolver();
    private static final RateLimitKeyResolver CART = new RateLimitKeyResolver() {
        public String name() {
            return "cart";
        }

        public Optional<String> resolve(HttpServletRequest request) {
            return Optional.ofNullable(request.getHeader("X-Cart"));
        }
    };

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;

    @Test
    void givenAClientOverThePolicyLimit_whenItSendsAnotherRequest_thenItIsLimitedWithARetryAfter() {
        RateLimiter limiter = limiter(policy("coupon", "/api/cart/coupon", List.of(), List.of("ip"), 2));

        assertThat(limiter.check(post("/api/cart/coupon", "1.1.1.1"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
        assertThat(limiter.check(post("/api/cart/coupon", "1.1.1.1"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
        RateLimiter.Decision third = limiter.check(post("/api/cart/coupon", "1.1.1.1"));

        assertThat(third).isInstanceOfSatisfying(RateLimiter.Decision.Limited.class, limited -> {
            assertThat(limited.policy()).isEqualTo("coupon");
            assertThat(limited.route()).isEqualTo("/api/cart/coupon");
            assertThat(limited.retryAfter()).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(30));
        });
    }

    @Test
    void givenTwoClients_whenOneIsLimited_thenTheOtherIsNot() {
        RateLimiter limiter = limiter(policy("coupon", "/api/cart/coupon", List.of(), List.of("ip"), 1));
        limiter.check(post("/api/cart/coupon", "1.1.1.1"));

        assertThat(limiter.check(post("/api/cart/coupon", "1.1.1.1"))).isInstanceOf(RateLimiter.Decision.Limited.class);
        assertThat(limiter.check(post("/api/cart/coupon", "2.2.2.2"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
    }

    @Test
    void givenACompositeKey_whenTheSameIpUsesAnotherCart_thenItGetsItsOwnBucket() {
        RateLimiter limiter = limiter(policy("checkout", "/api/checkout", List.of(), List.of("cart", "ip"), 1));
        MockHttpServletRequest cartA = post("/api/checkout", "1.1.1.1");
        cartA.addHeader("X-Cart", "A");
        MockHttpServletRequest cartB = post("/api/checkout", "1.1.1.1");
        cartB.addHeader("X-Cart", "B");
        limiter.check(cartA);

        assertThat(limiter.check(cartA)).isInstanceOf(RateLimiter.Decision.Limited.class);
        assertThat(limiter.check(cartB)).isInstanceOf(RateLimiter.Decision.Allowed.class);
    }

    @Test
    void givenAPolicyForSomeMethods_whenAnotherMethodIsUsed_thenItIsNotCounted() {
        RateLimiter limiter = limiter(policy("cart", "/api/cart/**", List.of("post"), List.of("ip"), 1));
        limiter.check(post("/api/cart/items", "1.1.1.1"));

        var get = new MockHttpServletRequest("GET", "/api/cart");
        get.setRemoteAddr("1.1.1.1");
        assertThat(limiter.check(get)).isInstanceOf(RateLimiter.Decision.Allowed.class);
        assertThat(limiter.check(post("/api/cart/items", "1.1.1.1"))).isInstanceOf(RateLimiter.Decision.Limited.class);
    }

    @Test
    void givenAnExcludedPath_whenAPolicyWouldMatchIt_thenItIsNeverLimited() {
        RateLimiter limiter = new RateLimiter(new RateLimitProperties(true, Duration.ofHours(1), 1000,
            List.of("/api/health", "/api/payments/*/webhook"),
            List.of(policy("all", "/api/**", List.of(), List.of("ip"), 1))), List.of(IP), ticker);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.check(post("/api/payments/mercadopago/webhook", "1.1.1.1")))
                .isInstanceOf(RateLimiter.Decision.Allowed.class);
        }
    }

    @Test
    void givenIdleBuckets_whenTheIdleExpiryPasses_thenTheyAreDroppedSoMemoryDoesNotGrow() {
        RateLimiter limiter = limiter(policy("coupon", "/api/cart/coupon", List.of(), List.of("ip"), 5));
        limiter.check(post("/api/cart/coupon", "1.1.1.1"));
        limiter.check(post("/api/cart/coupon", "2.2.2.2"));
        assertThat(limiter.bucketCount()).isEqualTo(2);

        nanos.addAndGet(Duration.ofHours(1).plusSeconds(1).toNanos());

        assertThat(limiter.bucketCount()).isZero();
    }

    @Test
    void givenLoginLimitedPerIpAndPerAccount_whenOneAccountIsAttackedFromManyIps_thenTheAccountLimitStillApplies() {
        RateLimiter limiter = new RateLimiter(new RateLimitProperties(true, Duration.ofHours(1), 1000, List.of(),
            List.of(policy("login-ip", "/api/login", List.of(), List.of("ip"), 5),
                policy("login-account", "/api/login", List.of(), List.of("cart"), 2))), List.of(IP, CART), ticker);

        assertThat(limiter.check(withCart(post("/api/login", "1.1.1.1"), "ana"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
        assertThat(limiter.check(withCart(post("/api/login", "2.2.2.2"), "ana"))).isInstanceOf(RateLimiter.Decision.Allowed.class);

        assertThat(limiter.check(withCart(post("/api/login", "3.3.3.3"), "ana")))
            .isInstanceOfSatisfying(RateLimiter.Decision.Limited.class,
                limited -> assertThat(limited.policy()).isEqualTo("login-account"));
        assertThat(limiter.check(withCart(post("/api/login", "3.3.3.3"), "bob"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
    }

    @Test
    void givenAPolicyWhoseKeyIsMissing_whenARequestArrives_thenThatPolicyDoesNotApplyButTheIpBaselineDoes() {
        RateLimiter limiter = new RateLimiter(new RateLimitProperties(true, Duration.ofHours(1), 1000, List.of(),
            List.of(policy("checkout-cart", "/api/checkout", List.of(), List.of("cart", "ip"), 1),
                policy("checkout-ip", "/api/checkout", List.of(), List.of("ip"), 3))), List.of(IP, CART), ticker);

        // Without a cart token, "checkout-cart" never shares one global bucket; the IP policy still limits.
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.check(post("/api/checkout", "1.1.1.1"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
        }
        assertThat(limiter.check(post("/api/checkout", "1.1.1.1")))
            .isInstanceOfSatisfying(RateLimiter.Decision.Limited.class,
                limited -> assertThat(limited.policy()).isEqualTo("checkout-ip"));
        assertThat(limiter.check(post("/api/checkout", "2.2.2.2"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
    }

    @Test
    void givenAGetOnlyPolicy_whenHeadRequestsAreSent_thenTheyCountAsGets() {
        RateLimiter limiter = limiter(policy("catalog", "/api/catalog/**", List.of("GET"), List.of("ip"), 1));
        var head = new MockHttpServletRequest("HEAD", "/api/catalog/products");
        head.setRemoteAddr("1.1.1.1");
        limiter.check(head);

        assertThat(limiter.check(head)).isInstanceOf(RateLimiter.Decision.Limited.class);
    }

    @Test
    void givenEncodedOrMatrixVariantsOfALimitedPath_whenSent_thenTheyCountAgainstTheSameRoute() {
        RateLimiter limiter = limiter(policy("coupon", "/api/cart/coupon", List.of(), List.of("ip"), 2));
        limiter.check(post("/api/cart/%63oupon", "1.1.1.1"));
        limiter.check(post("/api/cart/coupon;x=1", "1.1.1.1"));

        assertThat(limiter.check(post("/api/cart/coupon", "1.1.1.1"))).isInstanceOf(RateLimiter.Decision.Limited.class);
    }

    @Test
    void givenIpv6ClientsInTheSameSlash64_whenRotatingAddresses_thenTheyShareOneBucket() {
        RateLimiter limiter = limiter(policy("coupon", "/api/cart/coupon", List.of(), List.of("ip"), 1));
        limiter.check(post("/api/cart/coupon", "2001:db8:1:2:aaaa::1"));

        assertThat(limiter.check(post("/api/cart/coupon", "2001:db8:1:2:bbbb::2")))
            .isInstanceOf(RateLimiter.Decision.Limited.class);
        assertThat(limiter.check(post("/api/cart/coupon", "2001:db8:1:3::1")))
            .isInstanceOf(RateLimiter.Decision.Allowed.class);
    }

    @Test
    void givenMoreClientsThanTheBucketBound_whenTheyAllArrive_thenMemoryStaysBounded() {
        RateLimiter limiter = new RateLimiter(new RateLimitProperties(true, Duration.ofHours(1), 50, List.of(),
            List.of(policy("coupon", "/api/cart/coupon", List.of(), List.of("ip"), 1))), List.of(IP), ticker);

        for (int i = 0; i < 500; i++) {
            limiter.check(post("/api/cart/coupon", "10.0." + (i / 250) + "." + (i % 250)));
        }

        assertThat(limiter.bucketCount()).isLessThanOrEqualTo(50);
    }

    @Test
    void givenAPolicyWithAnUnknownKey_whenStarting_thenItFailsFast() {
        assertThatThrownBy(() -> limiter(policy("x", "/api/x", List.of(), List.of("account"), 1)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("account");
    }

    @Test
    void givenRateLimitingDisabled_whenOverTheLimit_thenNothingIsLimited() {
        RateLimiter limiter = new RateLimiter(new RateLimitProperties(false, Duration.ofHours(1), 1000, List.of(),
            List.of(policy("coupon", "/api/cart/coupon", List.of(), List.of("ip"), 1))), List.of(IP), ticker);
        limiter.check(post("/api/cart/coupon", "1.1.1.1"));

        assertThat(limiter.check(post("/api/cart/coupon", "1.1.1.1"))).isInstanceOf(RateLimiter.Decision.Allowed.class);
    }

    private RateLimiter limiter(Policy policy) {
        return new RateLimiter(new RateLimitProperties(true, Duration.ofHours(1), 1000, List.of(), List.of(policy)),
            List.of(IP, CART), ticker);
    }

    private static Policy policy(String name, String path, List<String> methods, List<String> keys, long capacity) {
        return new Policy(name, List.of(path), methods, keys, capacity, Duration.ofMinutes(1));
    }

    private static MockHttpServletRequest withCart(MockHttpServletRequest request, String cart) {
        request.addHeader("X-Cart", cart);
        return request;
    }

    private static MockHttpServletRequest post(String path, String ip) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        return request;
    }
}
