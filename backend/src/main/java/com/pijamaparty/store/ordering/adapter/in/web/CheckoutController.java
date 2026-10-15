package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.GetShippingCost;
import com.pijamaparty.store.ordering.application.OpenOrderPayment;
import com.pijamaparty.store.ordering.application.PlaceOrder;
import com.pijamaparty.store.ordering.domain.CheckoutForm;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.shipping.api.Province;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-12, UC-13, UC-14: the checkout of a visitor or customer. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/checkout")
@Tag(name = "Checkout")
class CheckoutController {

    /** The canonical text of a UUID, as the frontend's crypto.randomUUID() writes it. */
    private static final Pattern UUID_TEXT =
        Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Bounds the body; the domain applies the rules of UC-13 and answers 422 (RNF-28). */
    private static final int MAX_TEXT = 300;

    /** T-E3-14: the first request with the key ends within a second or two. */
    private static final String KEY_IN_USE_RETRY_AFTER_SECONDS = "1";

    private final GetShippingCost getShippingCost;
    private final PlaceOrder placeOrder;
    private final OpenOrderPayment openOrderPayment;
    private final TokenGenerator tokens;

    CheckoutController(GetShippingCost getShippingCost, PlaceOrder placeOrder, OpenOrderPayment openOrderPayment,
                       TokenGenerator tokens) {
        this.getShippingCost = getShippingCost;
        this.placeOrder = placeOrder;
        this.openOrderPayment = openOrderPayment;
        this.tokens = tokens;
    }

    @GetMapping("/shipping-cost")
    @Operation(summary = "The shipping cost of the cart to a province, and the summary amounts",
        description = "Step 1 of the checkout. The cart is priced as GET /api/cart: only AVAILABLE items count in the "
            + "subtotal, and the coupon is evaluated again. Shipping is free when the subtotal after the discount "
            + "reaches the free-shipping threshold. total = subtotal - discount + shipping. Amounts are in cents.")
    @ApiResponse(responseCode = "200", description = "The amounts", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ShippingCostResponse.class)))
    @ApiResponse(responseCode = "400", description = "Without province, or an unknown one",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "No cart, or none of its items available (problem EMPTY_CART)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "No shipping to the province (problem NO_SHIPPING, with "
        + "province)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> shippingCost(@RequestParam Province province) {
        var query = new GetShippingCost.Query(CurrentActor.get().orElseThrow(), province);
        return switch (getShippingCost.execute(query)) {
            case GetShippingCost.Result.Quoted quoted -> ResponseEntity.ok(ShippingCostResponse.of(quoted));
            case GetShippingCost.Result.NoShipping none -> noShipping(none.province());
            case GetShippingCost.Result.EmptyCart empty -> emptyCart();
        };
    }

    @PostMapping
    @Operation(summary = "Confirm the checkout: place the order, reserve its stock and open its payment",
        description = "Step 3 of the checkout. The cart becomes an order awaiting payment, its stock is reserved for "
            + "30 minutes and the cart is emptied. Every amount is the server's. trackingToken opens the order's "
            + "tracking page; it is shown only here. paymentUrl is the payment provider's checkout to redirect the "
            + "buyer to; null when the provider could not be reached, and then the client opens the tracking page. "
            + "The same Idempotency-Key repeated with the same content gets the same order, with a new "
            + "trackingToken, and nothing is placed or reserved again. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "The order, awaiting payment",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlacedResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key, or a body that is not of "
        + "this shape: a missing field, a text longer than 300 characters, an unknown province or provider; or a "
        + "provider switched off (problem PAYMENT_PROVIDER_NOT_ENABLED, with paymentProvider)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "See problem: EMPTY_CART, CART_NOT_READY (read the cart again), "
        + "COUPON_NOT_APPLICABLE (with couponStatus), INSUFFICIENT_STOCK (with variantId and available) or "
        + "IDEMPOTENCY_KEY_IN_USE (a request with the same key is still being processed: repeat it with the same key "
        + "after Retry-After seconds)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "See problem: INVALID_BUYER_DATA (with fields), "
        + "TERMS_NOT_ACCEPTED, NO_SHIPPING (with province) or IDEMPOTENCY_KEY_REUSED (the key went with a different "
        + "content)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> placeOrder(
        @Parameter(description = "A UUID per checkout attempt, repeated on retries") @RequestHeader("Idempotency-Key")
        String idempotencyKey,
        @Valid @RequestBody PlaceOrderRequest body) {
        if (!UUID_TEXT.matcher(idempotencyKey).matches()) {
            return problem(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key", "The Idempotency-Key must be a UUID.",
                null);
        }
        TrackingToken trackingToken = new TrackingToken(tokens.newToken());
        // UC-14: the new order belongs to a tracking token the buyer gets back; staff buy nothing.
        Actor actor = TrackingActors.withTrackingToken(CurrentActor.get().orElseThrow(), trackingToken);
        var command = new PlaceOrder.Command(actor, UUID.fromString(idempotencyKey), body.toForm(),
            body.address().province(), body.paymentProvider(), body.acceptedTerms());
        // The RLS context is set when the use case's transaction begins, so it must already carry the new token.
        return switch (CurrentActor.callAs(actor, () -> placeOrder.execute(command))) {
            case PlaceOrder.Result.Placed placed -> ResponseEntity.status(HttpStatus.CREATED)
                .body(new PlacedResponse(placed.number(), trackingToken.value(), OrderStatus.AWAITING_PAYMENT,
                    placed.total().cents(), placed.reservationExpiresAt(), paymentUrlOf(actor, placed)));
            case PlaceOrder.Result.InvalidBuyerData invalid -> {
                var problem = problemDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid buyer data",
                    "Some buyer or address data break the rules of the checkout.", "INVALID_BUYER_DATA");
                problem.setProperty("fields", invalid.fields().stream()
                    .sorted(Comparator.naturalOrder())
                    .map(CheckoutController::fieldName)
                    .toList());
                yield ResponseEntity.unprocessableEntity().body(problem);
            }
            case PlaceOrder.Result.PaymentProviderNotEnabled notEnabled -> {
                var problem = problemDetail(HttpStatus.BAD_REQUEST, "Payment provider not enabled",
                    "The store does not take payments with this provider.", "PAYMENT_PROVIDER_NOT_ENABLED");
                problem.setProperty("paymentProvider", notEnabled.provider().name());
                yield ResponseEntity.badRequest().body(problem);
            }
            case PlaceOrder.Result.TermsNotAccepted notAccepted -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Terms not accepted", "The terms must be accepted to place the order.", "TERMS_NOT_ACCEPTED");
            case PlaceOrder.Result.EmptyCart empty -> emptyCart();
            case PlaceOrder.Result.CartNotReady notReady -> problem(HttpStatus.CONFLICT, "Cart not ready",
                "Some item is not available or has a new price to acknowledge.", "CART_NOT_READY");
            case PlaceOrder.Result.CouponNotApplicable coupon -> {
                var problem = problemDetail(HttpStatus.CONFLICT, "Coupon not applicable",
                    "The coupon of the cart no longer applies.", "COUPON_NOT_APPLICABLE");
                problem.setProperty("couponStatus", coupon.status().name());
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
            case PlaceOrder.Result.NoShipping none -> noShipping(none.province());
            case PlaceOrder.Result.InsufficientStock insufficient -> {
                var problem = problemDetail(HttpStatus.CONFLICT, "Insufficient stock",
                    "A variant does not have enough stock.", "INSUFFICIENT_STOCK");
                problem.setProperty("variantId", insufficient.variantId());
                problem.setProperty("available", insufficient.available());
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
            case PlaceOrder.Result.KeyInUse inUse -> ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, KEY_IN_USE_RETRY_AFTER_SECONDS)
                .body(problemDetail(HttpStatus.CONFLICT, "Idempotency-Key in use",
                    "A request with the same Idempotency-Key is still being processed.", "IDEMPOTENCY_KEY_IN_USE"));
            case PlaceOrder.Result.KeyReused reused -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Idempotency-Key reused", "The Idempotency-Key was already used with a different content.",
                "IDEMPOTENCY_KEY_REUSED");
        };
    }

    /**
     * UC-14 (T-E3-06): once the order committed, its payment is opened outside any transaction, as the buyer that
     * holds the order's tracking token (Row Level Security lets it read only its own order). The order stands either
     * way: without a payment URL the buyer retries from the tracking page (T-E3-08).
     */
    private URI paymentUrlOf(Actor buyer, PlaceOrder.Result.Placed placed) {
        var command = new OpenOrderPayment.Command(placed.orderId());
        return switch (CurrentActor.callAs(buyer, () -> openOrderPayment.execute(command))) {
            case OpenOrderPayment.Result.Opened opened -> opened.paymentUrl();
            case OpenOrderPayment.Result.ProviderUnavailable unavailable -> null;
            case OpenOrderPayment.Result.NotPayable notPayable -> null;
        };
    }

    private static String fieldName(CheckoutForm.Field field) {
        return switch (field) {
            case NAME -> "name";
            case EMAIL -> "email";
            case PHONE -> "phone";
            case NATIONAL_ID -> "nationalId";
            case STREET -> "street";
            case NUMBER -> "number";
            case FLOOR -> "floor";
            case APARTMENT -> "apartment";
            case CITY -> "city";
            case POSTAL_CODE -> "postalCode";
        };
    }

    private static ResponseEntity<ProblemDetail> noShipping(Province province) {
        var problem = problemDetail(HttpStatus.UNPROCESSABLE_ENTITY, "No shipping",
            "The shop does not ship to this province.", "NO_SHIPPING");
        problem.setProperty("province", province.name());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> emptyCart() {
        return problem(HttpStatus.CONFLICT, "Empty cart", "The checkout needs a cart with available items.",
            "EMPTY_CART");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                         String code) {
        return ResponseEntity.status(status).body(problemDetail(status, title, detail, code));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (code != null) {
            problem.setProperty("problem", code);
        }
        return problem;
    }

    /** {@code totalCents} = {@code subtotalCents} − {@code discountCents} + {@code shippingCents}. */
    record ShippingCostResponse(Province province, long subtotalCents, long discountCents, long shippingCents,
                                long totalCents) {

        static ShippingCostResponse of(GetShippingCost.Result.Quoted quoted) {
            return new ShippingCostResponse(quoted.province(), quoted.subtotal().cents(), quoted.discount().cents(),
                quoted.shipping().cents(), quoted.total().cents());
        }
    }

    /** {@code paymentProvider}: MERCADOPAGO, or MODO while it is switched on (T-E3b-02). */
    record PlaceOrderRequest(@NotNull @Valid BuyerRequest buyer, @NotNull @Valid AddressRequest address,
                             @NotNull PaymentProvider paymentProvider, @NotNull Boolean acceptedTerms) {

        CheckoutForm toForm() {
            return new CheckoutForm(buyer.name(), buyer.email(), buyer.phone(), buyer.nationalId(), address.street(),
                address.number(), address.floor(), address.apartment(), address.city(), address.postalCode());
        }
    }

    /**
     * As typed: the server trims and normalizes. {@code phone} may carry spaces, '+', '-' and parentheses, and
     * {@code nationalId} dots and spaces.
     */
    record BuyerRequest(@NotNull @Size(max = MAX_TEXT) String name, @NotNull @Size(max = MAX_TEXT) String email,
                        @NotNull @Size(max = MAX_TEXT) String phone,
                        @NotNull @Size(max = MAX_TEXT) String nationalId) {}

    /** {@code floor} and {@code apartment} are optional; {@code postalCode} is 4 digits or a CPA such as S3000ABC. */
    record AddressRequest(@NotNull @Size(max = MAX_TEXT) String street, @NotNull @Size(max = MAX_TEXT) String number,
                          @Size(max = MAX_TEXT) String floor, @Size(max = MAX_TEXT) String apartment,
                          @NotNull @Size(max = MAX_TEXT) String city, @NotNull Province province,
                          @NotNull @Size(max = MAX_TEXT) String postalCode) {}

    /**
     * {@code trackingToken} is a secret of the buyer: the server keeps only its hash. Amounts in cents.
     * {@code paymentUrl} is the provider's checkout to redirect to, or null when the provider could not be reached:
     * then the client opens the tracking page, where the payment is retried.
     */
    record PlacedResponse(long number, String trackingToken, OrderStatus status, long totalCents,
                          Instant reservationExpiresAt,
                          @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) URI paymentUrl) {}
}
