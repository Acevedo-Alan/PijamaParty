package com.pijamaparty.store.cart.adapter.in.web;

import com.pijamaparty.store.cart.application.AcknowledgePriceChanges;
import com.pijamaparty.store.cart.application.AddItemToCart;
import com.pijamaparty.store.cart.application.ApplyCoupon;
import com.pijamaparty.store.cart.application.CartView;
import com.pijamaparty.store.cart.application.ChangeItemQuantity;
import com.pijamaparty.store.cart.application.CouponCheck;
import com.pijamaparty.store.cart.application.GetCart;
import com.pijamaparty.store.cart.application.ItemDescription;
import com.pijamaparty.store.cart.application.RemoveCoupon;
import com.pijamaparty.store.cart.application.RemoveItem;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.PricedCart.LineStatus;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-08, UC-09, UC-10: the visitor's or customer's cart. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart")
class CartController {

    private static final String CART_TOKEN_COOKIE = "A guest's __Host-cart_token cookie (HttpOnly, Secure, "
        + "SameSite=Lax, path /), set again so it lives 30 days from the last cart change, like the cart";

    private final GetCart getCart;
    private final AddItemToCart addItem;
    private final ChangeItemQuantity changeQuantity;
    private final RemoveItem removeItem;
    private final AcknowledgePriceChanges acknowledgePrices;
    private final ApplyCoupon applyCoupon;
    private final RemoveCoupon removeCoupon;
    private final TokenGenerator tokens;

    CartController(GetCart getCart, AddItemToCart addItem, ChangeItemQuantity changeQuantity, RemoveItem removeItem,
                   AcknowledgePriceChanges acknowledgePrices, ApplyCoupon applyCoupon, RemoveCoupon removeCoupon,
                   TokenGenerator tokens) {
        this.getCart = getCart;
        this.addItem = addItem;
        this.changeQuantity = changeQuantity;
        this.removeItem = removeItem;
        this.acknowledgePrices = acknowledgePrices;
        this.applyCoupon = applyCoupon;
        this.removeCoupon = removeCoupon;
        this.tokens = tokens;
    }

    @GetMapping
    @Operation(summary = "The current cart",
        description = "Its items in the order they were added, priced with the current prices and stock, and the "
            + "totals; empty when there is no cart yet. Only AVAILABLE items count in the subtotal and can be bought. "
            + "Prices are in cents.")
    @ApiResponse(responseCode = "200", description = "The cart", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = CartResponse.class)))
    CartResponse cart() {
        return CartResponse.of(getCart.execute(new GetCart.Query(actor())));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a variant to the cart",
        description = "Adds to the variant's line if it is already in the cart, and creates the cart with the first "
            + "product: a guest without a __Host-cart_token cookie gets one. The line can never exceed the available "
            + "stock. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart", headers = @Header(name = HttpHeaders.SET_COOKIE,
        description = CART_TOKEN_COOKIE), content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid body: quantity from 1 to 1,000,000",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "This actor cannot have a cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Unknown variant, or one the shop does not sell",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Not enough stock (problem INSUFFICIENT_STOCK): the units left "
        + "are in available",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> add(@Valid @RequestBody AddItemRequest body) {
        Actor actor = withCartToken(actor());
        var command = new AddItemToCart.Command(actor, VariantId.of(body.variantId()), body.quantity());
        // The RLS context is set when the use case's transaction begins, so it must already carry the new token.
        return switch (CurrentActor.callAs(actor, () -> addItem.execute(command))) {
            case AddItemToCart.Result.Added added -> cartChanged(actor, added.cart());
            case AddItemToCart.Result.InsufficientStock insufficient ->
                insufficientStock(insufficient.variantId(), insufficient.available());
            case AddItemToCart.Result.VariantUnavailable unavailable ->
                problem(HttpStatus.NOT_FOUND, "Variant not found");
            case AddItemToCart.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PatchMapping("/items/{id}")
    @Operation(summary = "Change the quantity of an item",
        description = "0 removes it. Any other quantity is checked against the available stock; a variant the shop no "
            + "longer sells has none. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart", headers = @Header(name = HttpHeaders.SET_COOKIE,
        description = CART_TOKEN_COOKIE), content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid body: quantity from 0 to 1,000,000",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "This actor cannot have a cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such item in the cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Not enough stock (problem INSUFFICIENT_STOCK): the units left "
        + "are in available",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> changeQuantity(@PathVariable UUID id, @Valid @RequestBody ChangeQuantityRequest body) {
        Actor actor = actor();
        var command = new ChangeItemQuantity.Command(actor, CartItemId.of(id), body.quantity());
        return switch (changeQuantity.execute(command)) {
            case ChangeItemQuantity.Result.Changed changed -> cartChanged(actor, changed.cart());
            case ChangeItemQuantity.Result.InsufficientStock insufficient ->
                insufficientStock(insufficient.variantId(), insufficient.available());
            case ChangeItemQuantity.Result.ItemNotFound missing -> itemNotFound();
            case ChangeItemQuantity.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Remove an item from the cart", description = "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart", headers = @Header(name = HttpHeaders.SET_COOKIE,
        description = CART_TOKEN_COOKIE), content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "403", description = "This actor cannot have a cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such item in the cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> remove(@PathVariable UUID id) {
        Actor actor = actor();
        return switch (removeItem.execute(new RemoveItem.Command(actor, CartItemId.of(id)))) {
            case RemoveItem.Result.Removed removed -> cartChanged(actor, removed.cart());
            case RemoveItem.Result.ItemNotFound missing -> itemNotFound();
            case RemoveItem.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/acknowledge-prices")
    @Operation(summary = "Acknowledge the price changes",
        description = "The customer saw the current prices: every item the shop sells records its current price, so "
            + "previousUnitPriceCents is no longer set. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart, empty when there is none",
        headers = @Header(name = HttpHeaders.SET_COOKIE, description = CART_TOKEN_COOKIE), content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "403", description = "This actor cannot have a cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> acknowledgePrices() {
        Actor actor = actor();
        return switch (acknowledgePrices.execute(new AcknowledgePriceChanges.Command(actor))) {
            case AcknowledgePriceChanges.Result.Acknowledged acknowledged -> cartChanged(actor, acknowledged.cart());
            case AcknowledgePriceChanges.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/coupon")
    @Operation(summary = "Apply a coupon code",
        description = "The code is matched ignoring surrounding spaces and case; an automatic coupon's code is "
            + "unknown. A coupon that applies to the cart's subtotal replaces the one the cart had; a rejected code "
            + "leaves the cart as it was. The discount is calculated on the server: an automatic coupon that takes more "
            + "still wins (appliedDiscount). Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart with the discount",
        headers = @Header(name = HttpHeaders.SET_COOKIE, description = CART_TOKEN_COOKIE), content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid body: a code of up to 100 characters",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "This actor cannot have a cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "No cart, or a cart without items (problem EMPTY_CART)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "The coupon does not apply (problem COUPON_UNKNOWN, "
        + "COUPON_INACTIVE, COUPON_NOT_STARTED, COUPON_EXPIRED, COUPON_USED_UP or COUPON_BELOW_MINIMUM, the last one "
        + "with missingCents: what the subtotal lacks to reach the minimum purchase)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Over 10 attempts per minute on this cart from this IP, or over "
        + "the cart-changes limit of this IP (RNF-22); Retry-After says when to retry",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> applyCoupon(@Valid @RequestBody ApplyCouponRequest body) {
        Actor actor = actor();
        return switch (applyCoupon.execute(new ApplyCoupon.Command(actor, body.code()))) {
            case ApplyCoupon.Result.Applied applied -> cartChanged(actor, applied.cart());
            case ApplyCoupon.Result.Unknown unknown -> couponRejected("COUPON_UNKNOWN", "Unknown coupon",
                "No coupon has this code.");
            case ApplyCoupon.Result.Inactive inactive -> couponRejected("COUPON_INACTIVE", "Inactive coupon",
                "The coupon is not active.");
            case ApplyCoupon.Result.NotStarted notStarted -> couponRejected("COUPON_NOT_STARTED",
                "Coupon not valid yet", "The coupon's validity has not started.");
            case ApplyCoupon.Result.Expired expired -> couponRejected("COUPON_EXPIRED", "Expired coupon",
                "The coupon's validity has ended.");
            case ApplyCoupon.Result.UsedUp usedUp -> couponRejected("COUPON_USED_UP", "Used-up coupon",
                "All the coupon's uses are taken.");
            case ApplyCoupon.Result.BelowMinimum below -> belowMinimum(below.missing());
            case ApplyCoupon.Result.EmptyCart empty -> emptyCart();
            case ApplyCoupon.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping("/coupon")
    @Operation(summary = "Remove the coupon",
        description = "Without a coupon it changes nothing. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart, empty when there is none",
        headers = @Header(name = HttpHeaders.SET_COOKIE, description = CART_TOKEN_COOKIE), content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "403", description = "This actor cannot have a cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Over the cart-changes limit of this IP (RNF-22); Retry-After "
        + "says when to retry",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> removeCoupon() {
        Actor actor = actor();
        return switch (removeCoupon.execute(new RemoveCoupon.Command(actor))) {
            case RemoveCoupon.Result.Removed removed -> cartChanged(actor, removed.cart());
            case RemoveCoupon.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    /** T-E2-04: a guest without a token gets one with its first product, so its new cart belongs to that token. */
    private Actor withCartToken(Actor actor) {
        if (!actor.is(Role.ANONYMOUS) || actor.cartToken().isPresent()) {
            return actor;
        }
        return Actor.anonymous(Optional.of(new CartToken(tokens.newToken())), actor.trackingToken());
    }

    /** The full cart; a guest's cookie is set again, so it lives as long as its cart (T-E2-04). */
    private static ResponseEntity<CartResponse> cartChanged(Actor actor, CartView cart) {
        var response = ResponseEntity.ok();
        if (actor.is(Role.ANONYMOUS)) {
            actor.cartToken()
                .ifPresent(token -> response.header(HttpHeaders.SET_COOKIE, CartTokenCookie.setCookie(token)));
        }
        return response.body(CartResponse.of(cart));
    }

    /** UC-08: the client shows the units left ("Solo quedan 3"). */
    private static ResponseEntity<ProblemDetail> insufficientStock(VariantId variantId, int available) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "The cart cannot hold more units than the available stock.");
        problem.setTitle("Insufficient stock");
        problem.setProperty("problem", "INSUFFICIENT_STOCK");
        problem.setProperty("variantId", variantId.value());
        problem.setProperty("available", available);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** UC-10: the client shows why ("El cupón venció"); {@code problem} tells the reasons apart. */
    private static ResponseEntity<ProblemDetail> couponRejected(String reason, String title, String detail) {
        return ResponseEntity.unprocessableEntity().body(couponProblem(reason, title, detail));
    }

    /** UC-10: the client tells how much is missing to reach the minimum purchase. */
    private static ResponseEntity<ProblemDetail> belowMinimum(Money missing) {
        var problem = couponProblem("COUPON_BELOW_MINIMUM", "Below the minimum purchase",
            "The cart's subtotal is below the coupon's minimum purchase.");
        problem.setProperty("missingCents", missing.cents());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ProblemDetail couponProblem(String reason, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problem.setTitle(title);
        problem.setProperty("problem", reason);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> emptyCart() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A coupon needs a cart with items.");
        problem.setTitle("Empty cart");
        problem.setProperty("problem", "EMPTY_CART");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> itemNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Cart item not found");
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title) {
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    record AddItemRequest(@NotNull UUID variantId, @NotNull @Min(1) @Max(Cart.MAX_QUANTITY) Integer quantity) {}

    /** {@code quantity} 0 removes the item. */
    record ChangeQuantityRequest(@NotNull @Min(0) @Max(Cart.MAX_QUANTITY) Integer quantity) {}

    /** {@code code} as the customer typed it. */
    record ApplyCouponRequest(@NotNull @Size(max = 100) String code) {}

    /**
     * {@code subtotalCents} counts only the {@code AVAILABLE} items; {@code discountCents} is the discount of
     * {@code appliedDiscount}, 0 without one; {@code totalCents} is subtotal − discount. Discounts never stack: the
     * one coupon that takes the most discounts, the code coupon on a tie (UC-23).
     */
    record CartResponse(List<ItemResponse> items, @Schema(nullable = true) CouponResponse coupon,
                        @Schema(nullable = true) AppliedDiscountResponse appliedDiscount, long subtotalCents,
                        long discountCents, long totalCents) {

        static CartResponse of(CartView cart) {
            return new CartResponse(cart.items().stream().map(ItemResponse::of).toList(),
                cart.coupon().map(CouponResponse::of).orElse(null),
                cart.appliedDiscount().map(AppliedDiscountResponse::of).orElse(null), cart.subtotal().cents(),
                cart.discount().cents(), cart.total().cents());
        }
    }

    /**
     * The code coupon the cart holds. It stays in the cart when it stops applying, with the reason in {@code status}
     * and no discount, and applies again on its own if the cart qualifies again. {@code missingCents} is what the
     * subtotal lacks to reach the minimum purchase when {@code status} is {@code BELOW_MINIMUM}, 0 otherwise. An
     * {@code APPLICABLE} coupon does not discount when {@code appliedDiscount} is an automatic coupon that takes more.
     */
    record CouponResponse(String code, CouponCheck.Status status, long missingCents) {

        static CouponResponse of(CartView.CouponView coupon) {
            return new CouponResponse(coupon.code(), coupon.status(), coupon.missing().cents());
        }
    }

    /**
     * UC-23: the coupon whose discount {@code discountCents} is, labeled by its {@code code} ("Descuento VERANO10"):
     * the held code coupon, or one that applied on its own ({@code automatic}).
     */
    record AppliedDiscountResponse(String code, boolean automatic) {

        static AppliedDiscountResponse of(CartView.AppliedDiscountView applied) {
            return new AppliedDiscountResponse(applied.code(), applied.automatic());
        }
    }

    /**
     * {@code unitPriceCents} is the current price, or the last seen one when the shop no longer sells the variant;
     * {@code previousUnitPriceCents} is the last seen price when the current one differs, a change to announce.
     * {@code product} is null only for a variant the catalog does not have.
     */
    record ItemResponse(UUID id, UUID variantId, int quantity, long unitPriceCents,
                        @Schema(nullable = true) Long previousUnitPriceCents, LineStatus status, int available,
                        @Schema(nullable = true) ItemProductResponse product) {

        static ItemResponse of(CartView.ItemView item) {
            return new ItemResponse(item.id().value(), item.variantId().value(), item.quantity(),
                item.unitPrice().cents(), item.previousUnitPrice().map(Money::cents).orElse(null), item.status(),
                item.available(), item.description().map(ItemProductResponse::of).orElse(null));
        }
    }

    /**
     * What the panel shows of an item's variant (UC-08), also when the shop no longer sells it: {@code image} is the
     * first photo of its color, or else of the whole product, or null without one.
     */
    record ItemProductResponse(String name, String slug, String size, String color, String sku,
                               @Schema(nullable = true) ItemImageResponse image) {

        static ItemProductResponse of(ItemDescription description) {
            return new ItemProductResponse(description.productName(), description.productSlug(), description.size(),
                description.color(), description.sku(), description.photo().map(ItemImageResponse::of).orElse(null));
        }
    }

    /** A photo as in the catalog (UC-06): each file is a WebP at most {@code width} pixels wide. */
    record ItemImageResponse(String placeholder, List<ItemImageFileResponse> files) {

        /** Where Caddy serves the uploads volume (deploy/caddy/Caddyfile), as the catalog's photos. */
        private static final String UPLOADS_PATH = "/uploads/";

        static ItemImageResponse of(ItemDescription.Photo photo) {
            return new ItemImageResponse(photo.placeholder(), photo.files().stream()
                .map(file -> new ItemImageFileResponse(file.width(), UPLOADS_PATH + file.fileName()))
                .toList());
        }
    }

    record ItemImageFileResponse(int width, String url) {}
}
