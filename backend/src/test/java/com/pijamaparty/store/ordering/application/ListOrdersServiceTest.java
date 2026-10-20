package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.ListOrders.Query;
import com.pijamaparty.store.ordering.application.ListOrders.Result;
import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListOrdersServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final ListOrdersService service = new ListOrdersService(orders);

    @Test
    void givenTheListOfOrders_whenFilteredByPaid_thenOnlyPaidOrdersAppear() {
        OrderId paid = placed(0);
        placed(1);
        orders.transition(paid, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW);

        assertThat(service.execute(new Query(ADMIN, Optional.of(OrderStatus.PAID), 0)))
            .isInstanceOfSatisfying(Result.Listed.class, listed -> {
                assertThat(listed.orders()).extracting(OrderSummary::id).containsExactly(paid);
                assertThat(listed.hasMore()).isFalse();
            });
    }

    @Test
    void givenMoreOrdersThanAPage_whenListed_thenFiftyComeNewestFirstAndTheRestOnTheNextPage() {
        List<OrderId> placed = IntStream.range(0, 51).mapToObj(this::placed).toList();

        Result first = service.execute(new Query(ADMIN, Optional.empty(), 0));
        Result second = service.execute(new Query(ADMIN, Optional.empty(), 1));

        assertThat(first).isInstanceOfSatisfying(Result.Listed.class, listed -> {
            assertThat(listed.orders()).hasSize(50).first().extracting(OrderSummary::id).isEqualTo(placed.get(50));
            assertThat(listed.hasMore()).isTrue();
        });
        assertThat(second).isInstanceOfSatisfying(Result.Listed.class, listed -> {
            assertThat(listed.orders()).extracting(OrderSummary::id).containsExactly(placed.get(0));
            assertThat(listed.hasMore()).isFalse();
        });
    }

    @Test
    void givenSomeoneButTheAdministrator_whenListing_thenItIsForbidden() {
        assertThat(service.execute(new Query(Actor.system(), Optional.empty(), 0))).isEqualTo(new Result.Forbidden());
    }

    /** An order placed {@code minutes} after NOW. */
    private OrderId placed(int minutes) {
        Instant at = NOW.plusSeconds(60L * minutes);
        OrderId id = OrderId.of(UUID.randomUUID());
        orders.insert(new NewOrder(id, orders.next(), hashLike(), Optional.empty(),
            new BuyerDetails("Ana María", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 1)),
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", at, StockHold.expiryOf(at)));
        return id;
    }

    /** A tracking token hash: 64 lowercase hex characters. */
    private static String hashLike() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
