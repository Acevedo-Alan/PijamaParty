package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.GetShippingCost;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.shipping.api.Province;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-12, UC-13: the checkout of a visitor or customer. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/checkout")
@Tag(name = "Checkout")
class CheckoutController {

    private final GetShippingCost getShippingCost;

    CheckoutController(GetShippingCost getShippingCost) {
        this.getShippingCost = getShippingCost;
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
            case GetShippingCost.Result.NoShipping none -> {
                var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The shop does not ship to this province.");
                problem.setTitle("No shipping");
                problem.setProperty("problem", "NO_SHIPPING");
                problem.setProperty("province", none.province().name());
                yield ResponseEntity.unprocessableEntity().body(problem);
            }
            case GetShippingCost.Result.EmptyCart empty -> {
                var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "The checkout needs a cart with available items.");
                problem.setTitle("Empty cart");
                problem.setProperty("problem", "EMPTY_CART");
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
        };
    }

    /** {@code totalCents} = {@code subtotalCents} − {@code discountCents} + {@code shippingCents}. */
    record ShippingCostResponse(Province province, long subtotalCents, long discountCents, long shippingCents,
                                long totalCents) {

        static ShippingCostResponse of(GetShippingCost.Result.Quoted quoted) {
            return new ShippingCostResponse(quoted.province(), quoted.subtotal().cents(), quoted.discount().cents(),
                quoted.shipping().cents(), quoted.total().cents());
        }
    }
}
