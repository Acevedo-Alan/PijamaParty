package com.pijamaparty.store.platform.web;

import com.pijamaparty.store.platform.logging.Pii;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders errors raised outside Spring MVC (filters, the request firewall, {@code sendError}) as problem+json too,
 * replacing Boot's default JSON. Only the status is returned: no path, message or trace.
 */
@RestController
class ProblemDetailsErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsErrorController.class);

    private final Saturation saturation;

    ProblemDetailsErrorController(Saturation saturation) {
        this.saturation = saturation;
    }

    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        Throwable exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable thrown
            ? thrown : null;
        if (saturation.causedBy(exception)) {
            log.warn("Pool exhausted outside Spring MVC, answering 503: {}", exception.getClass().getName());
            return withProblemJson(saturation.response());
        }
        HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
            && HttpStatus.resolve(code) != null ? HttpStatus.valueOf(code) : HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError() && exception != null) {
            log.error("Unexpected error outside Spring MVC", Pii.scrubException(exception));
        }
        return withProblemJson(ResponseEntity.status(status).body(ProblemDetail.forStatus(status)));
    }

    private static ResponseEntity<ProblemDetail> withProblemJson(ResponseEntity<ProblemDetail> response) {
        return ResponseEntity.status(response.getStatusCode())
            .headers(response.getHeaders())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(response.getBody());
    }
}
