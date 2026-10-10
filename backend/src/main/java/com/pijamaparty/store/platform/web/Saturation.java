package com.pijamaparty.store.platform.web;

import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * RNF-21: when a bounded pool is exhausted the API answers {@code 503} with {@code Retry-After} instead of hanging.
 * HikariCP gives up after its connection timeout and throws {@link SQLTransientConnectionException}, which Spring
 * wraps differently for JDBC and JPA, so the whole cause chain is inspected. Bounded executors (Spring's
 * {@code TaskRejectedException} included) reject with {@link RejectedExecutionException}.
 */
@Component
final class Saturation {

    private static final int MAX_CAUSES = 10;

    private final Duration retryAfter;

    Saturation(@Value("${store.saturation.retry-after:5s}") Duration retryAfter) {
        if (retryAfter.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("store.saturation.retry-after must be at least 1s: " + retryAfter);
        }
        this.retryAfter = retryAfter;
    }

    boolean causedBy(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSES; depth++) {
            if (current instanceof SQLTransientConnectionException || current instanceof RejectedExecutionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    ResponseEntity<ProblemDetail> response() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.toSeconds()))
            .body(ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
