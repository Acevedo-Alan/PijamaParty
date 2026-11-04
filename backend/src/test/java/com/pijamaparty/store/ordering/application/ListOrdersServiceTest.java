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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListOrdersServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final ListOrdersService service = new ListOrdersService(orders, Clock.fixed(NOW, STORE_ZONE));

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

    private List<OrderId> listed(Query query) {
        Result result = service.execute(query);
        assertThat(result).isInstanceOf(Result.Listed.class);
        return ((Result.Listed) result).orders().stream().map(OrderSummary::id).toList();
    }

    private static Query searching(String q) {
        return new Query(ADMIN, Optional.empty(), Optional.of(q), Optional.empty(), Optional.empty(), 0);
    }

    @Test
    void givenOrdersOneAndTwo_whenSearchingTheNumberWithOrWithoutTheHash_thenOnlyThatOneIsListed() {
        OrderId first = placed(0);
        OrderId second = placed(1);
        long secondNumber = numberOf(second);

        assertThat(listed(searching("#" + secondNumber))).containsExactly(second);
        assertThat(listed(searching(" " + secondNumber + " "))).containsExactly(second);
        assertThat(listed(searching(String.valueOf(numberOf(first))))).containsExactly(first);
    }

    @Test
    void givenABuyer_whenSearchingHerLastNameWithoutAccents_thenHerOrdersAreListed() {
        OrderId order = placed(0);

        assertThat(listed(searching("perez"))).containsExactly(order);
        assertThat(listed(searching("  ana maría  "))).containsExactly(order);
        assertThat(listed(searching("gómez"))).isEmpty();
    }

    @Test
    void givenASearchOf1CharacterOrOver100_whenListing_thenItIsInvalid() {
        assertThat(service.execute(searching("a"))).isEqualTo(new Result.InvalidSearch());
        assertThat(service.execute(searching("a".repeat(101)))).isEqualTo(new Result.InvalidSearch());
        assertThat(service.execute(searching("a".repeat(100)))).isInstanceOf(Result.Listed.class);
        assertThat(service.execute(searching("   "))).isInstanceOf(Result.Listed.class);
    }

    @Test
    void givenADayInArgentina_whenFiltered_thenAnOrderAt2359IsInsideAndOneAtMidnightOfTheNextDayIsNot() {
        // 2026-10-10 23:59 and 2026-10-11 00:00 in Argentina (UTC-3).
        OrderId lastMinute = placedAt(Instant.parse("2026-10-11T02:59:00Z"));
        placedAt(Instant.parse("2026-10-11T03:00:00Z"));
        OrderId firstMinute = placedAt(Instant.parse("2026-10-10T03:00:00Z"));
        placedAt(Instant.parse("2026-10-10T02:59:59Z"));
        LocalDate day = LocalDate.of(2026, 10, 10);

        assertThat(listed(new Query(ADMIN, Optional.empty(), Optional.empty(), Optional.of(day), Optional.of(day), 0)))
            .containsExactly(lastMinute, firstMinute);
    }

    @Test
    void givenFromAfterTo_whenListing_thenThePeriodIsInvalid() {
        var query = new Query(ADMIN, Optional.empty(), Optional.empty(), Optional.of(LocalDate.of(2026, 10, 11)),
            Optional.of(LocalDate.of(2026, 10, 10)), 0);

        assertThat(service.execute(query)).isEqualTo(new Result.InvalidPeriod());
    }

    @Test
    void givenASearchAndTheStatusPaid_whenBothApply_thenOnlyPaidOrdersMatchingTheSearchAreListed() {
        OrderId paid = placed(0);
        placed(1);
        orders.transition(paid, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW);

        assertThat(listed(new Query(ADMIN, Optional.of(OrderStatus.PAID), Optional.of("pérez"), Optional.empty(),
            Optional.empty(), 0))).containsExactly(paid);
    }

    @Test
    void givenASearch_whenTheQueryIsPrinted_thenItDoesNotCarryTheText() {
        assertThat(searching("ana@example.com").toString()).doesNotContain("ana@example.com");
    }

    @Test
    void givenSomeoneButTheAdministrator_whenListing_thenItIsForbidden() {
        assertThat(service.execute(new Query(Actor.system(), Optional.empty(), 0))).isEqualTo(new Result.Forbidden());
    }

    /** An order placed {@code minutes} after NOW. */
    private long numberOf(OrderId order) {
        return orders.page(OrderFilter.ALL, 0, 100).stream().filter(row -> row.id().equals(order)).findFirst()
            .orElseThrow().number();
    }

    private OrderId placed(int minutes) {
        return placedAt(NOW.plusSeconds(60L * minutes));
    }

    private OrderId placedAt(Instant at) {
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
