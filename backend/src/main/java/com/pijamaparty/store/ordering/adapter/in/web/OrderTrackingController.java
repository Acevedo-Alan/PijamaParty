package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.CorrectOrderEmail;
import com.pijamaparty.store.ordering.application.GetTrackedOrder;
import com.pijamaparty.store.ordering.application.RetryOrderPayment;
import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.shipping.api.Province;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-14 (T-E3-08): the tracking link of an order, opened with its token by a guest or a customer; UC-17 (T-E3-23): the
 * buyer corrects its email there. Only translates between HTTP and the use cases.
 */
@RestController
@RequestMapping("/api/orders/{token}")
@Tag(name = "Orders")
class OrderTrackingController {

    /** As {@link TokenGenerator} issues them; anything else is no order, without asking the database. */
    private static final Pattern TOKEN_TEXT = Pattern.compile("[A-Za-z0-9_-]{" + TokenGenerator.LENGTH + "}");

    /** MercadoPago is usually back within seconds; the buyer taps again. */
    static final String RETRY_AFTER_SECONDS = "30";

    /** Bounds the body; the domain applies the rule of UC-13 and answers 422 (RNF-28). */
    private static final int MAX_TEXT = 300;

    private final GetTrackedOrder getTrackedOrder;
    private final RetryOrderPayment retryOrderPayment;
    private final CorrectOrderEmail correctOrderEmail;

    OrderTrackingController(GetTrackedOrder getTrackedOrder, RetryOrderPayment retryOrderPayment,
                            CorrectOrderEmail correctOrderEmail) {
        this.getTrackedOrder = getTrackedOrder;
        this.retryOrderPayment = retryOrderPayment;
        this.correctOrderEmail = correctOrderEmail;
    }

    @GetMapping
    @Operation(summary = "The order of a tracking link",
        description = "What following the order needs, never the national ID, the email, the phone or the street: the "
            + "link can be forwarded. canRetryPayment is true while the order awaits payment and its stock is "
            + "reserved (until reservationExpiresAt). Amounts in cents. Not cached.")
    @ApiResponse(responseCode = "200", description = "The order", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = TrackedOrderResponse.class)))
    @ApiResponse(responseCode = "404", description = "No order has this token (problem ORDER_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> order(@PathVariable String token) {
        Optional<TrackingToken> trackingToken = parse(token);
        if (trackingToken.isEmpty()) {
            return orderNotFound();
        }
        var query = new GetTrackedOrder.Query(trackingToken.get());
        // The RLS context is set when the transaction begins, so the actor must already carry the token.
        return switch (CurrentActor.callAs(buyerOf(trackingToken.get()), () -> getTrackedOrder.execute(query))) {
            case GetTrackedOrder.Result.Found found -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(TrackedOrderResponse.of(found));
            case GetTrackedOrder.Result.NotFound notFound -> orderNotFound();
        };
    }

    @PostMapping("/retry-payment")
    @Operation(summary = "Pay again an order awaiting payment",
        description = "Answers the payment provider's checkout to redirect the buyer to: the same one while it "
            + "lasts, where another card can be tried. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The checkout to redirect to",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = RetryResponse.class)))
    @ApiResponse(responseCode = "404", description = "No order has this token (problem ORDER_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "No longer awaiting payment, or its reservation is over "
        + "(problem NOT_PAYABLE): read the order again",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "The payment provider could not be reached (problem "
        + "PAYMENT_PROVIDER_UNAVAILABLE): try again after Retry-After",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> retryPayment(@PathVariable String token) {
        Optional<TrackingToken> trackingToken = parse(token);
        if (trackingToken.isEmpty()) {
            return orderNotFound();
        }
        var command = new RetryOrderPayment.Command(trackingToken.get());
        return switch (CurrentActor.callAs(buyerOf(trackingToken.get()), () -> retryOrderPayment.execute(command))) {
            case RetryOrderPayment.Result.Opened opened -> ResponseEntity.ok(new RetryResponse(opened.paymentUrl()));
            case RetryOrderPayment.Result.NotFound notFound -> orderNotFound();
            case RetryOrderPayment.Result.NotPayable notPayable -> problem(HttpStatus.CONFLICT, "Not payable",
                "The order no longer awaits payment, or its reservation is over.", "NOT_PAYABLE");
            case RetryOrderPayment.Result.ProviderUnavailable unavailable -> ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider unavailable",
                    "The payment provider could not be reached. Try again in a moment.",
                    "PAYMENT_PROVIDER_UNAVAILABLE"));
        };
    }

    @PatchMapping("/email")
    @Operation(summary = "Correct the buyer's email of an order",
        description = "Replaces the email of the order of a tracking link. On a paid order (PAID to DELIVERED) the "
            + "confirmation is sent again to it; awaiting payment, the confirmation goes there once paid. Never "
            + "answers an email. Requires the CSRF token; 3 per hour per link and IP.")
    @ApiResponse(responseCode = "200", description = "Corrected; confirmationResent tells whether the confirmation "
        + "goes again now", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = EmailCorrectionResponse.class)))
    @ApiResponse(responseCode = "400", description = "A body that is not {email}, or an email longer than 300 "
        + "characters", content = @Content(mediaType = "application/problem+json",
        schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No order has this token (problem ORDER_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The order is cancelled or expired (problem ORDER_CLOSED, with "
        + "status): read the order again",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Not an email (problem INVALID_EMAIL)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> correctEmail(@PathVariable String token, @Valid @RequestBody EmailCorrectionRequest body) {
        Optional<TrackingToken> trackingToken = parse(token);
        if (trackingToken.isEmpty()) {
            return orderNotFound();
        }
        var command = new CorrectOrderEmail.Command(trackingToken.get(), body.email());
        return switch (CurrentActor.callAs(buyerOf(trackingToken.get()), () -> correctOrderEmail.execute(command))) {
            case CorrectOrderEmail.Result.Corrected corrected ->
                ResponseEntity.ok(new EmailCorrectionResponse(corrected.confirmationResent()));
            case CorrectOrderEmail.Result.NotFound notFound -> orderNotFound();
            case CorrectOrderEmail.Result.InvalidEmail invalid -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid email", "The email breaks the rules of the checkout.", "INVALID_EMAIL");
            case CorrectOrderEmail.Result.Closed closed -> {
                var problem = problemDetail(HttpStatus.CONFLICT, "Order closed",
                    "The order is cancelled or expired; its email is no longer corrected.", "ORDER_CLOSED");
                problem.setProperty("status", closed.status());
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
        };
    }

    private static Optional<TrackingToken> parse(String token) {
        return TOKEN_TEXT.matcher(token).matches() ? Optional.of(new TrackingToken(token)) : Optional.empty();
    }

    private static Actor buyerOf(TrackingToken trackingToken) {
        return TrackingActors.withTrackingToken(CurrentActor.get().orElseThrow(), trackingToken);
    }

    private static ResponseEntity<ProblemDetail> orderNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Order not found", "No order has this tracking link.",
            "ORDER_NOT_FOUND");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                         String code) {
        return ResponseEntity.status(status).body(problemDetail(status, title, detail, code));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return problem;
    }

    /**
     * {@code totalCents} = {@code subtotalCents} − {@code discountCents} + {@code shippingCents}. Of the buyer only
     * the first name, and of the address only the city and province.
     */
    record TrackedOrderResponse(long number, OrderStatus status, Instant createdAt, String buyerFirstName,
                                List<TrackedItemResponse> items, long subtotalCents, long discountCents,
                                @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
                                String couponCode,
                                long shippingCents, long totalCents, String city, Province province,
                                Instant reservationExpiresAt,
                                @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
                                String shipmentTrackingNumber,
                                boolean canRetryPayment) {

        static TrackedOrderResponse of(GetTrackedOrder.Result.Found found) {
            TrackedOrder order = found.order();
            var amounts = order.amounts();
            return new TrackedOrderResponse(order.number(), order.status(), order.createdAt(), order.buyerFirstName(),
                order.lines().stream().map(TrackedItemResponse::of).toList(), amounts.subtotal().cents(),
                amounts.discount().cents(), order.couponCode().orElse(null), amounts.shipping().cents(),
                amounts.total().cents(), order.city(), order.province(), order.reservationExpiresAt(),
                order.shipmentTrackingNumber().orElse(null), found.payable());
        }
    }

    /** {@code lineTotalCents} = {@code unitPriceCents} × {@code quantity}. */
    record TrackedItemResponse(String sku, String productName, String size, String color, int quantity,
                               long unitPriceCents, long lineTotalCents) {

        static TrackedItemResponse of(OrderLine line) {
            return new TrackedItemResponse(line.sku(), line.productName(), line.size(), line.color(), line.quantity(),
                line.unitPrice().cents(), line.amount().cents());
        }
    }

    /** The payment provider's checkout to redirect the buyer to. */
    record RetryResponse(URI paymentUrl) {}

    /** The email as typed: personal data (R-05), so {@link #toString()} hides it. */
    record EmailCorrectionRequest(@NotNull @Size(max = MAX_TEXT) String email) {

        @Override
        public String toString() {
            return "EmailCorrectionRequest[email=***]";
        }
    }

    /** Whether the order is paid and its confirmation goes again now, to the new email. */
    record EmailCorrectionResponse(boolean confirmationResent) {}
}
