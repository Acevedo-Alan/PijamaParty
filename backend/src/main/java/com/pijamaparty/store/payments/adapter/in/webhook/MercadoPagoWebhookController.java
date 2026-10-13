package com.pijamaparty.store.payments.adapter.in.webhook;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.ConfirmPayment;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-15: MercadoPago's Webhooks notifications. The signature is checked before anything else (RNF-29.3); then, as
 * {@code SYSTEM} (RNF-27), the payment is asked to MercadoPago and recorded. The body is not read: the notification
 * only carries ids, and the query carries the ones the signature covers. Logs carry the provider's payment id and the
 * order number only (R-05).
 */
@RestController
@Tag(name = "Payments")
class MercadoPagoWebhookController {

    static final String PATH = "/api/payments/mercadopago/webhook";

    /** MercadoPago's payment ids are numbers. */
    private static final Pattern PAYMENT_ID = Pattern.compile("[0-9]{1,20}");

    /** MercadoPago delivers a notification again every 15 minutes until it gets 200 or 201. */
    private static final String RETRY_AFTER_SECONDS = "60";

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoSignature signature;
    private final ConfirmPayment confirmPayment;

    MercadoPagoWebhookController(MercadoPagoSignature signature, ConfirmPayment confirmPayment) {
        this.signature = signature;
        this.confirmPayment = confirmPayment;
    }

    @PostMapping(PATH)
    @Operation(summary = "MercadoPago notifies that a payment changed",
        description = "Called by MercadoPago only, signed with the application's secret (x-signature, x-request-id). "
            + "The payment is asked to MercadoPago; an approved one for the order's exact total pays the order. "
            + "Repeating a notification has no further effects.")
    @ApiResponse(responseCode = "200", description = "Processed, or ignored (not a payment, or a payment this store "
        + "does not know)")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or out-of-date signature",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "MercadoPago could not be asked for the payment; it delivers the "
        + "notification again (Retry-After)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> notify(
        @Parameter(description = "The kind of resource, payment") @RequestParam(name = "type", required = false)
        String type,
        @Parameter(description = "MercadoPago's id of the payment") @RequestParam(name = "data.id", required = false)
        String dataId,
        @RequestHeader(name = "x-signature", required = false) String signatureHeader,
        @RequestHeader(name = "x-request-id", required = false) String requestId) {
        if (!signature.verify(signatureHeader, requestId, dataId)) {
            log.warn("MercadoPago notification refused: missing, invalid or out-of-date signature{}",
                signature.isConfigured() ? "" : " (store.payments.mercadopago.webhook-secret is empty)");
            return problem(HttpStatus.UNAUTHORIZED, "Invalid signature",
                "The notification is not signed by MercadoPago.");
        }
        if (!"payment".equals(type) || dataId == null || !PAYMENT_ID.matcher(dataId).matches()) {
            return ResponseEntity.ok().build();
        }
        var command = new ConfirmPayment.Command(PaymentProvider.MERCADOPAGO, dataId);
        return switch (CurrentActor.callAsSystem(() -> confirmPayment.execute(command))) {
            case ConfirmPayment.Result.Approved approved -> {
                log.info("MercadoPago payment {} approved order {}", dataId, approved.orderNumber());
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.AlreadyApproved already -> ResponseEntity.ok().build();
            case ConfirmPayment.Result.Rejected rejected -> {
                log.info("MercadoPago payment {} of order {} was rejected", dataId, rejected.orderNumber());
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.Unchanged unchanged -> ResponseEntity.ok().build();
            case ConfirmPayment.Result.AmountMismatch mismatch -> {
                log.error("MercadoPago payment {} of order {} was approved for {} instead of {}: the order is not paid",
                    dataId, mismatch.orderNumber(), mismatch.paid(), mismatch.expected());
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.UnknownPayment unknown -> {
                log.warn("MercadoPago payment {} is not for an order of this store: ignored", dataId);
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.NotFoundAtProvider notFound -> {
                log.warn("MercadoPago does not know payment {}: ignored", dataId);
                yield ResponseEntity.ok().build();
            }
            case ConfirmPayment.Result.ProviderUnavailable unavailable -> ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Provider unavailable",
                    "MercadoPago could not be asked for the payment; deliver the notification again."));
        };
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
