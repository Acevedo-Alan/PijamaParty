package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindAdminOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final FindAdminOrderService service = new FindAdminOrderService(orders);
    private final OrderId order = OrderId.of(UUID.randomUUID());

    @Test
    void givenAPaidOrder_whenFound_thenItOffersPreparingAndCancelling() {
        placed();
        orders.transition(order, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW);

        assertThat(service.find(order.value())).map(AdminOrder::nextStatuses)
            .contains(List.of(Status.PREPARING, Status.CANCELLED));
    }

    @Test
    void givenAnExpiredOrder_whenFound_thenItOffersNothing() {
        placed();
        orders.transition(order, OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, NOW);

        assertThat(service.find(order.value())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(Status.EXPIRED);
            assertThat(found.nextStatuses()).isEmpty();
        });
    }

    @Test
    void givenNoSuchOrder_whenFound_thenThereIsNone() {
        assertThat(service.find(UUID.randomUUID())).isEmpty();
    }

    private void placed() {
        orders.insert(new NewOrder(order, orders.next(), hashLike(), Optional.empty(),
            new BuyerDetails("Ana María Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 1)),
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", NOW, StockHold.expiryOf(NOW)));
    }

    /** A tracking token hash: 64 lowercase hex characters. */
    private static String hashLike() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
