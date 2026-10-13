package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The order of a tracking-link request, for the payment-retry policy of T-E3-08 (RNF-22 "Pedido + IP"): the token of
 * the path {@code /api/orders/{token}/...}. The key is the token's SHA-256, so not even the limiter's memory holds the
 * token. A path without a well-formed token has no order and the policy does not apply; the tracking policy per IP
 * is the baseline.
 */
@Component
class TrackingKeyResolver implements RateLimitKeyResolver {

    private static final Pattern ORDER_PATH = Pattern.compile(
        "^/api/orders/([A-Za-z0-9_-]{" + TokenGenerator.LENGTH + "})(?:/.*)?$");

    @Override
    public String name() {
        return "tracking";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        Matcher path = ORDER_PATH.matcher(request.getRequestURI().substring(request.getContextPath().length()));
        return path.matches() ? Optional.of(new TrackingToken(path.group(1))).map(AccessToken::hash) : Optional.empty();
    }
}
