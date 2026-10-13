package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.ChangeOrderStatus;
import com.pijamaparty.store.ordering.application.CorrectShipmentTrackingNumber;
import com.pijamaparty.store.ordering.application.ListOrders;
import com.pijamaparty.store.ordering.application.OrderSummary;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.shipping.api.Province;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-18: the orders in the back office: the list, the status changes and the tracking number. The detail is composed
 * in the backoffice module. Only translates between HTTP and the use cases.
 */
@RestController
@RequestMapping("/api/admin/orders")
@Tag(name = "Back office orders")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class OrderAdminController {

    private static final int MAX_PAGE = 10_000;

    private final ListOrders listOrders;
    private final ChangeOrderStatus changeStatus;
    private final CorrectShipmentTrackingNumber correctTrackingNumber;

    OrderAdminController(ListOrders listOrders, ChangeOrderStatus changeStatus,
                         CorrectShipmentTrackingNumber correctTrackingNumber) {
        this.listOrders = listOrders;
        this.changeStatus = changeStatus;
        this.correctTrackingNumber = correctTrackingNumber;
    }

    @GetMapping
    @Operation(summary = "The orders, newest first, optionally only those in a status",
        description = "50 per page, from page 0.")
    @ApiResponse(responseCode = "200", description = "One page of orders", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = OrderPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown status or page out of range",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> list(@RequestParam(required = false) OrderStatus status,
                           @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (listOrders.execute(new ListOrders.Query(actor(), Optional.ofNullable(status), page))) {
            case ListOrders.Result.Listed listed -> ResponseEntity.ok(new OrderPageResponse(
                listed.orders().stream().map(OrderSummaryResponse::of).toList(), listed.hasMore()));
            case ListOrders.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Move an order from the status the administrator saw to another one",
        description = "SHIPPED needs shipmentTrackingNumber; cancelling a PAID or PREPARING order needs restock "
            + "(put every line's stock back, or none). Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Changed", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = OrderStatusResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such order (problem ORDER_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "See problem: ORDER_STATUS_CHANGED (the order is no longer in "
        + "from; currentStatus), INVALID_TRANSITION",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "See problem: MISSING_TRACKING_NUMBER, INVALID_TRACKING_NUMBER, "
        + "RESTOCK_DECISION_REQUIRED, UNEXPECTED_FIELD (field)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> changeStatus(@PathVariable UUID id, @Valid @RequestBody OrderStatusChangeRequest body) {
        var command = new ChangeOrderStatus.Command(actor(), OrderId.of(id), body.from(), body.to(),
            Optional.ofNullable(body.shipmentTrackingNumber()), Optional.ofNullable(body.restock()));
        return switch (changeStatus.execute(command)) {
            case ChangeOrderStatus.Result.Changed changed ->
                ResponseEntity.ok(new OrderStatusResponse(changed.status()));
            case ChangeOrderStatus.Result.Forbidden forbidden -> forbidden();
            case ChangeOrderStatus.Result.NotFound notFound -> notFound();
            case ChangeOrderStatus.Result.StatusChanged stale -> statusChanged(stale.current());
            case ChangeOrderStatus.Result.InvalidTransition invalid -> problem(HttpStatus.CONFLICT,
                "Invalid transition", "The state diagram does not allow this change.", "INVALID_TRANSITION");
            case ChangeOrderStatus.Result.MissingTrackingNumber missing -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Missing tracking number", "Shipping an order needs its tracking number.", "MISSING_TRACKING_NUMBER");
            case ChangeOrderStatus.Result.InvalidTrackingNumber invalid -> invalidTrackingNumber();
            case ChangeOrderStatus.Result.RestockDecisionRequired required -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Restock decision required", "Cancelling a paid or preparing order needs restock: true or false.",
                "RESTOCK_DECISION_REQUIRED");
            case ChangeOrderStatus.Result.UnexpectedField unexpected -> {
                var problem = problemDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Unexpected field",
                    "This change does not take " + unexpected.field() + ".", "UNEXPECTED_FIELD");
                problem.setProperty("field", unexpected.field());
                yield ResponseEntity.unprocessableEntity().body(problem);
            }
        };
    }

    @PutMapping("/{id}/shipment-tracking-number")
    @Operation(summary = "Correct the tracking number of a shipped order",
        description = "Only while the order is SHIPPED. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Corrected", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ShipmentTrackingNumberResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such order (problem ORDER_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The order is not SHIPPED (problem ORDER_NOT_SHIPPED; "
        + "currentStatus)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Blank or longer than 60 characters "
        + "(problem INVALID_TRACKING_NUMBER)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> correctTrackingNumber(@PathVariable UUID id,
                                            @Valid @RequestBody ShipmentTrackingNumberRequest body) {
        var command = new CorrectShipmentTrackingNumber.Command(actor(), OrderId.of(id),
            body.shipmentTrackingNumber());
        return switch (correctTrackingNumber.execute(command)) {
            case CorrectShipmentTrackingNumber.Result.Corrected corrected ->
                ResponseEntity.ok(new ShipmentTrackingNumberResponse(corrected.number().value()));
            case CorrectShipmentTrackingNumber.Result.Forbidden forbidden -> forbidden();
            case CorrectShipmentTrackingNumber.Result.NotFound notFound -> notFound();
            case CorrectShipmentTrackingNumber.Result.NotShipped notShipped -> {
                var problem = problemDetail(HttpStatus.CONFLICT, "Order not shipped",
                    "Only a shipped order's tracking number can be corrected.", "ORDER_NOT_SHIPPED");
                problem.setProperty("currentStatus", notShipped.current().name());
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
            case CorrectShipmentTrackingNumber.Result.InvalidTrackingNumber invalid -> invalidTrackingNumber();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> statusChanged(OrderStatus current) {
        var problem = problemDetail(HttpStatus.CONFLICT, "Order status changed",
            "The order changed since it was read; read it again.", "ORDER_STATUS_CHANGED");
        problem.setProperty("currentStatus", current.name());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> invalidTrackingNumber() {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid tracking number",
            "A tracking number has 1 to 60 characters.", "INVALID_TRACKING_NUMBER");
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Order not found", "There is no such order.", "ORDER_NOT_FOUND");
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
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

    record OrderPageResponse(List<OrderSummaryResponse> items, boolean hasMore) {}

    /** Personal data (R-05): the buyer's name and city. */
    record OrderSummaryResponse(UUID id, long number, Instant createdAt, OrderStatus status, String buyerName,
                                String city, Province province, long totalCents) {

        static OrderSummaryResponse of(OrderSummary order) {
            return new OrderSummaryResponse(order.id().value(), order.number(), order.createdAt(), order.status(),
                order.buyerName(), order.city(), order.province(), order.total().cents());
        }

        @Override
        public String toString() {
            return "OrderSummaryResponse[number=" + number + ", ***]";
        }
    }

    /**
     * @param from the status the administrator saw
     * @param shipmentTrackingNumber only for SHIPPED; trimmed, 1 to 60 characters
     * @param restock only when cancelling a PAID or PREPARING order
     */
    record OrderStatusChangeRequest(@NotNull OrderStatus from, @NotNull OrderStatus to,
                                    @Schema(nullable = true) @Size(max = 200) String shipmentTrackingNumber,
                                    @Schema(nullable = true) Boolean restock) {}

    record OrderStatusResponse(OrderStatus status) {}

    /** @param shipmentTrackingNumber trimmed, 1 to 60 characters */
    record ShipmentTrackingNumberRequest(@NotNull @Size(max = 200) String shipmentTrackingNumber) {}

    record ShipmentTrackingNumberResponse(String shipmentTrackingNumber) {}
}
