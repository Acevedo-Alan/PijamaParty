package com.pijamaparty.store.platform.web.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/** Answers {@code 429} with {@code Retry-After} in problem+json when a policy is exceeded (RNF-22, R-10). */
final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    /** At most one log line per policy in this window, so an attacker cannot flood the disk through the limiter. */
    private static final long LOG_WINDOW_NANOS = Duration.ofSeconds(10).toNanos();

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final Map<String, LogWindow> logWindows = new ConcurrentHashMap<>();

    RateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        // Wrapped so a key resolver may read the body (the sign-in email) and the controller still sees all of it.
        var peekable = new PeekableRequest(request);
        if (limiter.check(peekable) instanceof RateLimiter.Decision.Limited limited) {
            logRejection(limited);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfter().toSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS));
            return;
        }
        chain.doFilter(peekable, response);
    }

    /** The policy and the route pattern only: never the IP, the account or the actual path (RNF-22). */
    private void logRejection(RateLimiter.Decision.Limited limited) {
        LogWindow window = logWindows.computeIfAbsent(limited.policy(), policy -> new LogWindow());
        long suppressed = window.recordAndCheck(System.nanoTime());
        if (suppressed >= 0) {
            log.warn("Rate limit exceeded: policy={} route={} (+{} more in the last window)",
                limited.policy(), limited.route(), suppressed);
        }
    }

    private static final class LogWindow {
        private final AtomicLong startedAt = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong rejections = new AtomicLong();

        /** Returns how many rejections were not logged in the previous window, or -1 if this one is not logged. */
        long recordAndCheck(long now) {
            long started = startedAt.get();
            if (started == Long.MIN_VALUE || now - started >= LOG_WINDOW_NANOS) {
                if (startedAt.compareAndSet(started, now)) {
                    return rejections.getAndSet(0);
                }
            }
            rejections.incrementAndGet();
            return -1;
        }
    }
}
