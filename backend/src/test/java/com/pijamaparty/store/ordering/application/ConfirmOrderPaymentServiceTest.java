package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.api.OrderPaid;
import com.pijamaparty.store.ordering.application.ConfirmOrderPayment.Command;
import com.pijamaparty.store.ordering.application.ConfirmOrderPayment.Result;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmOrderPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:10:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(1200);

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryStockReleases stock = new InMemoryStockReleases();
    private final InMemoryOrderCouponRedemptions coupons = new InMemoryOrderCouponRedemptions();
    private final InMemoryOrderEvents events = new InMemoryOrderEvents();
    private final ConfirmOrderPaymentService service = new ConfirmOrderPaymentService(orders, orders, stock, coupons,
        events, Clock.fixed(NOW, ZoneOffset.UTC));
    private final OrderId order = OrderId.of(UUID.randomUUID());
    private final UUID variant = UUID.randomUUID();
    private final UUID coupon = UUID.randomUUID();

    @Test
    void givenAnOrderAwaitingPayment_whenItsPaymentIsApproved_thenItIsPaidItsStockDeductedAndItsCouponUsed() {
        long number = orders.add(order, OrderStatus.AWAITING_PAYMENT, EXPIRES, Optional.of(coupon));
        stock.reserved(order, variant, 3, 2);
        coupons.coupon(coupon);

        Result result = service.execute(new Command(order));

        assertThat(result).isEqualTo(new Result.Paid(number));
        assertThat(orders.statusOf(order)).contains(OrderStatus.PAID);
        assertThat(stock.onHandOf(variant)).isOne();
        assertThat(stock.availableOf(variant)).isOne();
        assertThat(coupons.usesOf(coupon)).isOne();
        assertThat(events.published()).containsExactly(new OrderPaid(order.value(), number));
    }

    @Test
    void givenAnOrderWithoutACoupon_whenPaid_thenNoCouponGetsAUse() {
        long number = orders.add(order, OrderStatus.AWAITING_PAYMENT, EXPIRES);
        stock.reserved(order, variant, 3, 2);
        coupons.coupon(coupon);

        assertThat(service.execute(new Command(order))).isEqualTo(new Result.Paid(number));
        assertThat(coupons.usesOf(coupon)).isZero();
    }

    @Test
    void givenAPaidOrder_whenItsApprovalArrivesAgain_thenNothingIsDeductedOrUsedTwice() {
        long number = orders.add(order, OrderStatus.AWAITING_PAYMENT, EXPIRES, Optional.of(coupon));
        stock.reserved(order, variant, 3, 2);
        coupons.coupon(coupon);
        service.execute(new Command(order));

        Result again = service.execute(new Command(order));

        assertThat(again).isEqualTo(new Result.AlreadyPaid(number));
        assertThat(events.published()).hasSize(1);
        assertThat(stock.onHandOf(variant)).isOne();
        assertThat(coupons.usesOf(coupon)).isOne();
    }

    @Test
    void givenAnOrderFurtherOn_whenAnApprovalArrives_thenItKeepsItsStatus() {
        for (OrderStatus status : new OrderStatus[] {OrderStatus.PAID, OrderStatus.PREPARING}) {
            OrderId further = OrderId.of(UUID.randomUUID());
            long number = orders.add(further, status, EXPIRES);

            assertThat(service.execute(new Command(further))).isEqualTo(new Result.AlreadyPaid(number));
            assertThat(orders.statusOf(further)).contains(status);
        }
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAnExpiredOrder_whenItsPaymentIsApprovedLate_thenItStaysExpiredAndItsStockIsNotTouched() {
        long number = orders.add(order, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(60), Optional.of(coupon));
        stock.reserved(order, variant, 3, 2);
        coupons.coupon(coupon);
        new ExpireOverdueOrderService(orders, orders, stock, Clock.fixed(NOW, ZoneOffset.UTC)).execute();

        Result result = service.execute(new Command(order));

        assertThat(result).isEqualTo(new Result.PaidAfterClosing(number, OrderStatus.EXPIRED));
        assertThat(events.published()).isEmpty();
        assertThat(orders.statusOf(order)).contains(OrderStatus.EXPIRED);
        assertThat(stock.onHandOf(variant)).isEqualTo(3);
        assertThat(stock.availableOf(variant)).isEqualTo(3);
        assertThat(coupons.usesOf(coupon)).isZero();
    }

    @Test
    void givenACancelledOrder_whenItsPaymentIsApproved_thenItStaysCancelled() {
        long number = orders.add(order, OrderStatus.CANCELLED, EXPIRES);

        assertThat(service.execute(new Command(order)))
            .isEqualTo(new Result.PaidAfterClosing(number, OrderStatus.CANCELLED));
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenNoSuchOrder_whenAnApprovalArrives_thenItFails() {
        assertThatThrownBy(() -> service.execute(new Command(order))).isInstanceOf(IllegalStateException.class);
    }
}
