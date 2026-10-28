package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.CustomerOrderSummary;
import com.pijamaparty.store.ordering.application.CustomerOrderViews.CustomerOrder;
import com.pijamaparty.store.ordering.application.GetMyOrder;
import com.pijamaparty.store.ordering.application.LinePhotos;
import com.pijamaparty.store.ordering.application.ListMyOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.shipping.api.Province;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-22 (T-E4-07): "Mis pedidos" of the signed-in customer. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/account/orders")
@Tag(name = "Customer account")
class CustomerOrdersController {

    private static final int MAX_PAGE = 1_000;

    private final ListMyOrders listMyOrders;
    private final GetMyOrder getMyOrder;

    CustomerOrdersController(ListMyOrders listMyOrders, GetMyOrder getMyOrder) {
        this.listMyOrders = listMyOrders;
        this.getMyOrder = getMyOrder;
    }

    @GetMapping
    @Operation(summary = "The customer's orders, newest first",
        description = ListMyOrders.PAGE_SIZE + " per page, from page 0. Not cached.")
    @ApiResponse(responseCode = "200", description = "One page of orders; empty when there are none",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MyOrderPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Page out of range",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in as a customer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> list(@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (listMyOrders.execute(new ListMyOrders.Query(actor(), page))) {
            case ListMyOrders.Result.Listed listed -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MyOrderPageResponse(listed.orders().stream().map(MyOrderSummaryResponse::of).toList(),
                    listed.hasMore()));
            case ListMyOrders.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of the customer's orders, with its tracking",
        description = "The items, each with the photo the catalog shows now of its variant or null, the amounts in "
            + "cents, the order's own copy of the address and the shipment tracking number once shipped. Not cached.")
    @ApiResponse(responseCode = "200", description = "The order", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = MyOrderResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in as a customer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such order of this customer, whether it does not exist or "
        + "is another person's (problem ORDER_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> order(@PathVariable UUID id) {
        return switch (getMyOrder.execute(new GetMyOrder.Query(actor(), OrderId.of(id)))) {
            case GetMyOrder.Result.Found found -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(MyOrderResponse.of(found.order(), found.photos()));
            case GetMyOrder.Result.NotFound notFound -> problem(HttpStatus.NOT_FOUND, "Order not found",
                "ORDER_NOT_FOUND");
            case GetMyOrder.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> notSignedIn() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String code) {
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return ResponseEntity.status(status).body(problem);
    }

    record MyOrderPageResponse(List<MyOrderSummaryResponse> orders, boolean hasMore) {}

    record MyOrderSummaryResponse(UUID id, long number, Instant createdAt, OrderStatus status, long totalCents) {

        static MyOrderSummaryResponse of(CustomerOrderSummary order) {
            return new MyOrderSummaryResponse(order.id().value(), order.number(), order.createdAt(), order.status(),
                order.total().cents());
        }
    }

    /** {@code totalCents} = {@code subtotalCents} − {@code discountCents} + {@code shippingCents}. */
    record MyOrderResponse(UUID id, long number, OrderStatus status, Instant createdAt, List<MyOrderItemResponse> items,
                           long subtotalCents, long discountCents,
                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
                           String couponCode,
                           long shippingCents, long totalCents, MyOrderAddressResponse address,
                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
                           String shipmentTrackingNumber) {

        static MyOrderResponse of(CustomerOrder order, Map<UUID, LinePhotos.Photo> photos) {
            var amounts = order.amounts();
            return new MyOrderResponse(order.id().value(), order.number(), order.status(), order.createdAt(),
                order.lines().stream().map(line -> MyOrderItemResponse.of(line, photos)).toList(),
                amounts.subtotal().cents(),
                amounts.discount().cents(), order.couponCode().orElse(null), amounts.shipping().cents(),
                amounts.total().cents(), MyOrderAddressResponse.of(order.address(), order.province()),
                order.shipmentTrackingNumber().orElse(null));
        }

        @Override
        public String toString() {
            return "MyOrderResponse[number=" + number + ", status=" + status + ", address=***]";
        }
    }

    /**
     * {@code lineTotalCents} = {@code unitPriceCents} × {@code quantity}. {@code image} is the photo the catalog shows
     * now of the variant, or null.
     */
    record MyOrderItemResponse(String sku, String productName, String size, String color, int quantity,
                               long unitPriceCents, long lineTotalCents,
                               @Schema(nullable = true) OrderItemImageResponse image) {

        static MyOrderItemResponse of(OrderLine line, Map<UUID, LinePhotos.Photo> photos) {
            LinePhotos.Photo photo = photos.get(line.variantId());
            return new MyOrderItemResponse(line.sku(), line.productName(), line.size(), line.color(), line.quantity(),
                line.unitPrice().cents(), line.amount().cents(),
                photo == null ? null : OrderItemImageResponse.of(photo));
        }
    }

    /** The order's own copy of the address (R-04). Personal data (R-05): {@link #toString()} hides it. */
    record MyOrderAddressResponse(String street, String number,
                                  @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String floor,
                                  @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
                                  String apartment,
                                  String city, Province province, String postalCode) {

        static MyOrderAddressResponse of(ShippingAddress address, Province province) {
            return new MyOrderAddressResponse(address.street(), address.number(), address.floor().orElse(null),
                address.apartment().orElse(null), address.city(), province, address.postalCode());
        }

        @Override
        public String toString() {
            return "MyOrderAddressResponse[***]";
        }
    }
}
