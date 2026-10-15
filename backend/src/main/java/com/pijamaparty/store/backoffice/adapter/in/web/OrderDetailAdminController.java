package com.pijamaparty.store.backoffice.adapter.in.web;

import com.pijamaparty.store.backoffice.application.GetOrderDetail;
import com.pijamaparty.store.backoffice.domain.OrderAlert;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** UC-18: the back-office order detail with its alerts. Only translates between HTTP and the use case. */
@RestController
@Tag(name = "Back office orders")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class OrderDetailAdminController {

    private final GetOrderDetail getOrderDetail;

    OrderDetailAdminController(GetOrderDetail getOrderDetail) {
        this.getOrderDetail = getOrderDetail;
    }

    @GetMapping("/api/admin/orders/{id}")
    @Operation(summary = "An order with its buyer, address, lines, amounts, next statuses and alerts")
    @ApiResponse(responseCode = "200", description = "The order", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdminOrderResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such order (problem ORDER_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> get(@PathVariable UUID id) {
        return switch (getOrderDetail.execute(new GetOrderDetail.Query(CurrentActor.get().orElseThrow(), id))) {
            case GetOrderDetail.Result.Found found ->
                ResponseEntity.ok(AdminOrderResponse.of(found.order(), found.alerts()));
            case GetOrderDetail.Result.NotFound notFound -> {
                var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "There is no such order.");
                problem.setTitle("Order not found");
                problem.setProperty("problem", "ORDER_NOT_FOUND");
                yield ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
            }
            case GetOrderDetail.Result.Forbidden forbidden ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
        };
    }

    /**
     * Personal data (R-05): the buyer and the address; {@link #toString()} prints only the number.
     *
     * @param nextStatuses what the administrator may move the order to
     * @param paymentProvider e.g. MERCADOPAGO
     * @param alerts most urgent first: PAID_AFTER_CLOSING (refund it from the provider's panel), BUYER_EMAIL_BOUNCED
     *     and BUYER_EMAIL_FAILED (call the buyer)
     */
    record AdminOrderResponse(UUID id, long number, Status status, List<Status> nextStatuses, Instant createdAt,
                              Instant updatedAt, @Schema(nullable = true) Instant deliveredAt,
                              AdminOrderBuyerResponse buyer, AdminOrderAddressResponse address,
                              List<AdminOrderLineResponse> lines, long subtotalCents, long discountCents,
                              @Schema(nullable = true) String couponCode, long shippingCents, long totalCents,
                              String paymentProvider, @Schema(nullable = true) String shipmentTrackingNumber,
                              List<OrderAlert> alerts) {

        static AdminOrderResponse of(AdminOrder order, List<OrderAlert> alerts) {
            var buyer = order.buyer();
            return new AdminOrderResponse(order.orderId(), order.number(), order.status(), order.nextStatuses(),
                order.createdAt(), order.updatedAt(), order.deliveredAt().orElse(null),
                new AdminOrderBuyerResponse(buyer.name(), buyer.email(), buyer.phone(), buyer.nationalId()),
                AdminOrderAddressResponse.of(order.address()),
                order.lines().stream().map(AdminOrderLineResponse::of).toList(), order.subtotal().cents(),
                order.discount().cents(), order.couponCode().orElse(null), order.shipping().cents(),
                order.total().cents(), order.paymentProvider(), order.shipmentTrackingNumber().orElse(null), alerts);
        }

        @Override
        public String toString() {
            return "AdminOrderResponse[number=" + number + ", ***]";
        }
    }

    record AdminOrderBuyerResponse(String name, String email, String phone, String nationalId) {

        @Override
        public String toString() {
            return "AdminOrderBuyerResponse[***]";
        }
    }

    /** @param province the constant of the province, e.g. SANTA_FE */
    record AdminOrderAddressResponse(String street, String number, @Schema(nullable = true) String floor,
                                     @Schema(nullable = true) String apartment, String city, String province,
                                     String postalCode) {

        static AdminOrderAddressResponse of(Address address) {
            return new AdminOrderAddressResponse(address.street(), address.number(), address.floor().orElse(null),
                address.apartment().orElse(null), address.city(), address.province(), address.postalCode());
        }

        @Override
        public String toString() {
            return "AdminOrderAddressResponse[***]";
        }
    }

    record AdminOrderLineResponse(String productName, String size, String color, int quantity, long unitPriceCents,
                                  long amountCents) {

        static AdminOrderLineResponse of(Line line) {
            return new AdminOrderLineResponse(line.productName(), line.size(), line.color(), line.quantity(),
                line.unitPrice().cents(), line.amount().cents());
        }
    }
}
