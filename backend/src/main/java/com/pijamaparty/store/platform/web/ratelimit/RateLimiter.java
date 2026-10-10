package com.pijamaparty.store.platform.web.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Decides whether a request may proceed under the configured RNF-22 policies. Buckets live in memory only. */
final class RateLimiter {

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private final boolean enabled;
    private final List<PathPattern> excluded;
    private final List<CompiledPolicy> policies;
    private final Cache<String, Bucket> buckets;

    RateLimiter(RateLimitProperties properties, List<RateLimitKeyResolver> resolvers, Ticker ticker) {
        Map<String, RateLimitKeyResolver> byName = resolvers.stream()
            .collect(Collectors.toMap(RateLimitKeyResolver::name, Function.identity()));
        this.enabled = properties.enabled();
        this.excluded = properties.excludedPaths().stream().map(PARSER::parse).toList();
        this.policies = properties.policies().stream().map(policy -> compile(policy, byName)).toList();
        this.buckets = Caffeine.newBuilder()
            .expireAfterAccess(properties.idleExpiry())
            .maximumSize(properties.maxBuckets())
            .ticker(ticker)
            .build();
    }

    Decision check(HttpServletRequest request) {
        if (!enabled) {
            return new Decision.Allowed();
        }
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        if (excluded.stream().anyMatch(pattern -> pattern.matches(path))) {
            return new Decision.Allowed();
        }
        // Every matching policy counts (for example login per IP and, separately, per account: RNF-29.5); the
        // request is rejected if any of them is exhausted, with the longest wait.
        Decision decision = new Decision.Allowed();
        for (CompiledPolicy policy : policies) {
            PathPattern matched = policy.match(request.getMethod(), path);
            Optional<String> bucketKey = matched == null ? Optional.empty() : policy.bucketKey(request);
            if (bucketKey.isEmpty()) {
                continue;
            }
            ConsumptionProbe probe = buckets.get(bucketKey.get(), key -> policy.newBucket())
                .tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                var limited = new Decision.Limited(policy.name(), matched.getPatternString(), retryAfter(probe));
                if (!(decision instanceof Decision.Limited previous)
                    || limited.retryAfter().compareTo(previous.retryAfter()) > 0) {
                    decision = limited;
                }
            }
        }
        return decision;
    }

    long bucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private static Duration retryAfter(ConsumptionProbe probe) {
        long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        return Duration.ofSeconds(seconds);
    }

    private static CompiledPolicy compile(RateLimitProperties.Policy policy, Map<String, RateLimitKeyResolver> byName) {
        if (policy.name() == null || policy.paths().isEmpty() || policy.keys().isEmpty()
            || policy.capacity() <= 0 || policy.period() == null || policy.period().isZero()
            || policy.period().isNegative()) {
            throw new IllegalStateException("Incomplete rate-limit policy: " + policy.name());
        }
        List<RateLimitKeyResolver> keys = policy.keys().stream().map(name -> {
            RateLimitKeyResolver resolver = byName.get(name);
            if (resolver == null) {
                throw new IllegalStateException("Unknown rate-limit key '" + name + "' in policy " + policy.name());
            }
            return resolver;
        }).toList();
        Set<String> methods = policy.methods().stream().map(method -> method.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        return new CompiledPolicy(policy.name(), policy.paths().stream().map(PARSER::parse).toList(), methods, keys,
            policy.capacity(), policy.period());
    }

    /** The outcome for one request. */
    sealed interface Decision {
        record Allowed() implements Decision {
        }

        /** {@code route} is the matched pattern, safe to log; never the actual path, which may hold tokens. */
        record Limited(String policy, String route, Duration retryAfter) implements Decision {
        }
    }

    private record CompiledPolicy(String name, List<PathPattern> paths, Set<String> methods,
                                  List<RateLimitKeyResolver> keys, long capacity, Duration period) {

        PathPattern match(String method, PathContainer path) {
            // Spring MVC serves HEAD with the GET handler, so it costs the same and counts the same.
            String effective = "HEAD".equals(method) ? "GET" : method;
            if (!methods.isEmpty() && !methods.contains(effective)) {
                return null;
            }
            return paths.stream().filter(pattern -> pattern.matches(path)).findFirst().orElse(null);
        }

        /**
         * Empty when a key part is missing: the policy does not apply, instead of every such request sharing one
         * global bucket that anyone could exhaust. An IP-keyed policy on the same route is the baseline.
         */
        Optional<String> bucketKey(HttpServletRequest request) {
            var parts = new StringBuilder(name);
            for (RateLimitKeyResolver resolver : keys) {
                Optional<String> part = resolver.resolve(request);
                if (part.isEmpty()) {
                    return Optional.empty();
                }
                parts.append('|').append(part.get());
            }
            return Optional.of(parts.toString());
        }

        Bucket newBucket() {
            return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
        }
    }
}
