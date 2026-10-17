package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-22 (T-E4-08): at sign-in, the guest orders of the customer's verified email join its account. */
class LinkGuestOrdersServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final UserId ANA = UserId.of(UUID.randomUUID());

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryVerifiedCustomerEmails verifiedEmails = new InMemoryVerifiedCustomerEmails();
    private final LinkGuestOrdersService service =
        new LinkGuestOrdersService(verifiedEmails, orders, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAGuestOrderWithTheSameVerifiedEmail_whenTheCustomerSignsIn_thenTheOrderShowsInItsList() {
        verifiedEmails.verified(ANA, "ana@example.com");
        NewOrder guests = guestOrder("ana@example.com");
        guestOrder("bruno@example.com");

        var result = service.execute(new LinkGuestOrders.Command(ANA));

        assertThat(result).isEqualTo(new LinkGuestOrders.Result.Linked(1));
        assertThat(orders.page(ANA, 0, 20)).extracting(CustomerOrderSummary::id).containsExactly(guests.id());
        assertThat(orders.updatedAtOf(guests.id())).contains(NOW);
    }

    @Test
    void givenAnEmailNotVerified_whenTheCustomerSignsIn_thenNoOrderIsLinked() {
        NewOrder guests = guestOrder("ana@example.com");

        var result = service.execute(new LinkGuestOrders.Command(ANA));

        assertThat(result).isEqualTo(new LinkGuestOrders.Result.NotVerified());
        assertThat(orders.userOf(guests.id())).isEmpty();
    }

    @Test
    void givenNoGuestOrders_whenTheCustomerSignsIn_thenNoneIsLinked() {
        verifiedEmails.verified(ANA, "ana@example.com");

        assertThat(service.execute(new LinkGuestOrders.Command(ANA)))
            .isEqualTo(new LinkGuestOrders.Result.Linked(0));
    }

    private NewOrder guestOrder(String email) {
        Instant placedAt = NOW.minusSeconds(3_600);
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(), "a".repeat(63) + orders.placedCount(),
            Optional.empty(), new BuyerDetails("Ana Pérez", email, "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "3000"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000),
                1)),
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", placedAt, StockHold.expiryOf(placedAt));
        orders.insert(order);
        return order;
    }
}
