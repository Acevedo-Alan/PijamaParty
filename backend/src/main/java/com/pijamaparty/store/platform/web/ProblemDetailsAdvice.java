package com.pijamaparty.store.platform.web;

import com.pijamaparty.store.platform.logging.Pii;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place that turns exceptions into {@code application/problem+json} (RNF-05). Spring MVC errors
 * (validation, malformed body, unknown route, wrong method...) keep their status; anything unexpected becomes a
 * generic 500 that never reveals internals such as SQL (RNF-28). Expected business failures are not exceptions:
 * each controller maps its use case {@code Result} itself.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    /** Thrown inside a controller (for example by method security): the same 401/403 as the filter chain. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated() {
        return ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied() {
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error", Pii.scrubException(exception));
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
