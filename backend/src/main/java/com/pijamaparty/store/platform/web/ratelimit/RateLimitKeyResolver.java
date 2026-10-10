package com.pijamaparty.store.platform.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * One part of a rate-limit bucket key, referenced by {@link #name()} from a policy's {@code keys}. New keys (cart
 * token, normalized account, order) are new beans in the stage that needs them; nothing here changes.
 * Values stay in memory only and are never logged.
 */
public interface RateLimitKeyResolver {

    String name();

    /** Empty when the request does not carry this part (for example no cart yet). */
    Optional<String> resolve(HttpServletRequest request);
}
