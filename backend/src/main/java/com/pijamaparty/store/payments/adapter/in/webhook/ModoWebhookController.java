package com.pijamaparty.store.payments.adapter.in.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.payments.adapter.out.modo.ModoPaymentRequestIds;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.ConfirmPayment;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-15 (T-E3b-03): MODO's notifications of its payment requests. The signature is checked before anything else
 * (RNF-29.3), and only the signed payload is read: the unsigned rest of the body is ignored. {@code ACCEPTED} and
 * {@code REJECTED} are then, as {@code SYSTEM} (RNF-27), asked to MODO and recorded; the other statuses change
 * nothing. The body carries the payer's name, national ID and card digits: it is never logged or stored, and logs
 * carry the payment request id and the order number only (R-05).
 */
@RestController
@Tag(name = "Payments")
class ModoWebhookController {

    static final String PATH = "/api/payments/modo/webhook";

    /** The statuses that end a payment request; MODO retries only these. */
    private static final Set<String> FINAL_STATUSES = Set.of("ACCEPTED", "REJECTED");

    /** A notification is a couple of kilobytes; a larger body is not read. */
    private static final int MAX_BODY_LENGTH = 64 * 1024;

    /** MODO retries within 14 seconds whatever this says; the reconciliation asks again within a minute. */
    private static final String RETRY_AFTER_SECONDS = "60";

    private static final Logger log = LoggerFactory.getLogger(ModoWebhookController.class);

    private final ModoSignature signature;
    private final ConfirmPayment confirmPayment;
    private final ObjectMapper json;

    ModoWebhookController(ModoSignature signature, ConfirmPayment confirmPayment, ObjectMapper json) {
        this.signature = signature;
        this.confirmPayment = confirmPayment;
        this.json = json;
    }

    @PostMapping(PATH)
    @Operation(operationId = "notifyModo", summary = "MODO notifies that a payment request changed",
        description = "Called by MODO only, signed with a JWS in the body's signature field, verified against MODO's "
            + "JWK set. On ACCEPTED or REJECTED the payment request is asked to MODO; an approved one for the order's "
            + "exact total pays the order. Repeating a notification has no further effects.")
    @ApiResponse(responseCode = "200", description = "Processed, or ignored (a status that ends nothing, or a payment "
        + "request this store does not know)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid signature",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "MODO could not be asked for the payment request (Retry-After)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> notify(@RequestBody(required = false) String body) {
        Optional<JsonNode> payload = signedPayload(body);
        if (payload.isEmpty()) {
            log.warn("MODO notification refused: missing or invalid signature");
            return problem(HttpStatus.UNAUTHORIZED, "Invalid signature", "The notification is not signed by MODO.");
        }
        String id = payload.get().path("id").asText("");
        String status = payload.get().path("status").asText("");
        if (!FINAL_STATUSES.contains(status) || !ModoPaymentRequestIds.isValid(id)) {
            return ResponseEntity.ok().build();
        }
        var command = new ConfirmPayment.Command(PaymentProvider.MODO, id);
        return switch (CurrentActor.callAsSystem(() -> confirmPayment.execute(command))) {
            case ConfirmPayment.Result.Approved approved -> {
                log.info("MODO payment request {} approved order {}", id, approved.orderNumber());
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.AlreadyApproved already -> ResponseEntity.ok().build();
            case ConfirmPayment.Result.Rejected rejected -> {
                log.info("MODO payment request {} of order {} was rejected", id, rejected.orderNumber());
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.Unchanged unchanged -> ResponseEntity.ok().build();
            case ConfirmPayment.Result.AmountMismatch mismatch -> {
                log.error("MODO payment request {} of order {} was approved for {} instead of {}: the order is not "
                    + "paid", id, mismatch.orderNumber(), mismatch.paid(), mismatch.expected());
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.UnknownPayment unknown -> {
                log.warn("MODO payment request {} is not a checkout of this store: ignored", id);
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.AlreadyProcessed already -> ResponseEntity.ok().build();
            case ConfirmPayment.Result.NotFoundAtProvider notFound -> {
                log.warn("MODO does not show payment request {}: ignored", id);
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.ProviderUnavailable unavailable -> ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Provider unavailable",
                    "MODO could not be asked for the payment request; deliver the notification again."));
        };
    }

    /** The JSON that MODO signed, when the body carries a valid signature of it. */
    private Optional<JsonNode> signedPayload(String body) {
        if (body == null || body.length() > MAX_BODY_LENGTH) {
            return Optional.empty();
        }
        try {
            JsonNode notification = json.readTree(body);
            if (notification == null || !notification.isObject()) {
                return Optional.empty();
            }
            Optional<String> payload = signature.verifiedPayload(notification.get("signature"));
            if (payload.isEmpty()) {
                return Optional.empty();
            }
            JsonNode signed = json.readTree(payload.get());
            return signed != null && signed.isObject() ? Optional.of(signed) : Optional.empty();
        } catch (JsonProcessingException malformed) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status).body(problemDetail(status, title, detail));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
