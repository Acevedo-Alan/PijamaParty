package com.pijamaparty.store.platform.web.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate-limit policies (RNF-22), tuned without recompiling. Each stage adds the policies of the endpoints it creates.
 *
 * <pre>
 * store.rate-limit.policies:
 *   - name: checkout
 *     paths: [/api/checkout]
 *     methods: [POST]
 *     keys: [cart, ip]
 *     capacity: 5
 *     period: 1m
 * </pre>
 *
 * @param idleExpiry    a bucket nobody used for this long is dropped, so memory does not grow
 * @param maxBuckets    hard bound on the number of buckets kept in memory
 * @param excludedPaths never limited (webhooks and health, which the provider signature protects)
 * @param policies      every policy matching the request counts; the request is rejected if any is exhausted.
 *                      A policy whose key cannot be resolved (no cart token, no account) does not apply, so each
 *                      route should also have an IP-keyed policy as its baseline. Keys of authenticated users must
 *                      come from the session: this filter runs before Spring Security.
 */
@ConfigurationProperties("store.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("1h") Duration idleExpiry,
    @DefaultValue("100000") long maxBuckets,
    @DefaultValue List<String> excludedPaths,
    @DefaultValue List<Policy> policies) {

    public RateLimitProperties {
        excludedPaths = List.copyOf(excludedPaths);
        policies = List.copyOf(policies);
    }

    /**
     * @param name     appears in logs instead of the client data
     * @param paths    Spring path patterns, e.g. {@code /api/catalog/**}
     * @param methods  HTTP methods; empty means all
     * @param keys     names of {@link RateLimitKeyResolver}s whose values identify the bucket (e.g. {@code [cart, ip]})
     * @param capacity requests allowed per period
     * @param period   time to refill the full capacity
     */
    public record Policy(String name, List<String> paths, @DefaultValue List<String> methods, List<String> keys,
                         long capacity, Duration period) {
        public Policy {
            paths = paths == null ? List.of() : List.copyOf(paths);
            methods = methods == null ? List.of() : List.copyOf(methods);
            keys = keys == null ? List.of() : List.copyOf(keys);
        }
    }
}
