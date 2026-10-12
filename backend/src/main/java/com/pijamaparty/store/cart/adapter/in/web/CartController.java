package com.pijamaparty.store.cart.adapter.in.web;

import com.pijamaparty.store.cart.application.AddItemToCart;
import com.pijamaparty.store.cart.application.CartView;
import com.pijamaparty.store.cart.application.ChangeItemQuantity;
import com.pijamaparty.store.cart.application.GetCart;
import com.pijamaparty.store.cart.application.RemoveItem;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
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

    private final GetCart getCart;
    private final AddItemToCart addItem;
    private final ChangeItemQuantity changeQuantity;
    private final RemoveItem removeItem;

    CartController(GetCart getCart, AddItemToCart addItem, ChangeItemQuantity changeQuantity, RemoveItem removeItem) {
        this.getCart = getCart;
        this.addItem = addItem;
        this.changeQuantity = changeQuantity;
        this.removeItem = removeItem;
    }

    @GetMapping
    @Operation(summary = "The current cart",
        description = "Its items in the order they were added; empty when there is no cart yet. Prices are in cents.")
    @ApiResponse(responseCode = "200", description = "The cart", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = CartResponse.class)))
    CartResponse cart() {
        return CartResponse.of(getCart.execute(new GetCart.Query(actor())));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a variant to the cart",
        description = "Adds to the variant's line if it is already in the cart, and creates the cart with the first "
            + "product. The line can never exceed the available stock. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart", content = @Content(
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
        var command = new AddItemToCart.Command(actor(), VariantId.of(body.variantId()), body.quantity());
        return switch (addItem.execute(command)) {
            case AddItemToCart.Result.Added added -> ResponseEntity.ok(CartResponse.of(added.cart()));
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
    @ApiResponse(responseCode = "200", description = "The full cart", content = @Content(
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
        var command = new ChangeItemQuantity.Command(actor(), CartItemId.of(id), body.quantity());
        return switch (changeQuantity.execute(command)) {
            case ChangeItemQuantity.Result.Changed changed -> ResponseEntity.ok(CartResponse.of(changed.cart()));
            case ChangeItemQuantity.Result.InsufficientStock insufficient ->
                insufficientStock(insufficient.variantId(), insufficient.available());
            case ChangeItemQuantity.Result.ItemNotFound missing -> itemNotFound();
            case ChangeItemQuantity.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Remove an item from the cart", description = "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The full cart", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "403", description = "This actor cannot have a cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such item in the cart",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> remove(@PathVariable UUID id) {
        return switch (removeItem.execute(new RemoveItem.Command(actor(), CartItemId.of(id)))) {
            case RemoveItem.Result.Removed removed -> ResponseEntity.ok(CartResponse.of(removed.cart()));
            case RemoveItem.Result.ItemNotFound missing -> itemNotFound();
            case RemoveItem.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
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

    record CartResponse(List<ItemResponse> items) {

        static CartResponse of(CartView cart) {
            return new CartResponse(cart.items().stream().map(ItemResponse::of).toList());
        }
    }

    /** {@code unitPriceCents} is the price the customer last saw. */
    record ItemResponse(UUID id, UUID variantId, int quantity, long unitPriceCents) {

        static ItemResponse of(CartView.ItemView item) {
            return new ItemResponse(item.id().value(), item.variantId().value(), item.quantity(),
                item.unitPrice().cents());
        }
    }
}
