package com.pijamaparty.store.cart.adapter.in.web;

import com.pijamaparty.store.cart.application.AcknowledgePriceChanges;
import com.pijamaparty.store.cart.application.AddItemToCart;
import com.pijamaparty.store.cart.application.CartView;
import com.pijamaparty.store.cart.application.ChangeItemQuantity;
import com.pijamaparty.store.cart.application.GetCart;
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

/** UC-08, UC-09: the visitor's or customer's cart. Only translates between HTTP and the use cases. */
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
    private final TokenGenerator tokens;

    CartController(GetCart getCart, AddItemToCart addItem, ChangeItemQuantity changeQuantity, RemoveItem removeItem,
                   AcknowledgePriceChanges acknowledgePrices, TokenGenerator tokens) {
        this.getCart = getCart;
        this.addItem = addItem;
        this.changeQuantity = changeQuantity;
        this.removeItem = removeItem;
        this.acknowledgePrices = acknowledgePrices;
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

    /** {@code subtotalCents} counts only the {@code AVAILABLE} items; {@code totalCents} is subtotal − discount. */
    record CartResponse(List<ItemResponse> items, long subtotalCents, long discountCents, long totalCents) {

        static CartResponse of(CartView cart) {
            return new CartResponse(cart.items().stream().map(ItemResponse::of).toList(), cart.subtotal().cents(),
                cart.discount().cents(), cart.total().cents());
        }
    }

    /**
     * {@code unitPriceCents} is the current price, or the last seen one when the shop no longer sells the variant;
     * {@code previousUnitPriceCents} is the last seen price when the current one differs, a change to announce.
     */
    record ItemResponse(UUID id, UUID variantId, int quantity, long unitPriceCents,
                        @Schema(nullable = true) Long previousUnitPriceCents, LineStatus status, int available) {

        static ItemResponse of(CartView.ItemView item) {
            return new ItemResponse(item.id().value(), item.variantId().value(), item.quantity(),
                item.unitPrice().cents(), item.previousUnitPrice().map(Money::cents).orElse(null), item.status(),
                item.available());
        }
    }
}
