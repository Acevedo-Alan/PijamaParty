package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.api.Province;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerOrderExports} (UC-28, T-E4-16): every order of a customer, newest first, with everything
 * the order keeps of the buyer, and none of another customer's.
 */
public abstract class CustomerOrderExportsContractTest<T extends SaveOrders & OrderNumbers & CustomerOrderExports>
    extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-17T15:00:00.123456Z");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ShippingAddress ADDRESS = new ShippingAddress("San Martín", "1234", Optional.of("3"),
        Optional.of("B"), "Santa Fe", "3000");
    private static final List<OrderLine> LINES = List.of(
        new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2),
        new OrderLine(UUID.randomUUID(), "CA-S-AZUL", "Camisón Sol", "S", "Azul", Money.of(1_500_000), 1));

    /** A customer of the shop. */
    protected abstract UserId customer(String email);

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    @Test
    void givenTwoOrdersOfTheCustomerAndOneOfAnother_whenExporting_thenItGetsItsOwnNewestFirstWithTheBuyersCopy() {
        UserId ana = customer("ana@example.com");
        UserId bruno = customer("bruno@example.com");
        NewOrder older = placed(ana, NOW.minusSeconds(3600));
        NewOrder newer = placed(ana, NOW);
        placed(bruno, NOW);
        persistAndForget();

        List<ExportedOrder> exported = subject.all(ana);

        assertThat(exported).extracting(ExportedOrder::id).containsExactly(newer.id(), older.id());
        ExportedOrder first = exported.getFirst();
        assertThat(first.number()).isEqualTo(newer.number());
        assertThat(first.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(first.createdAt()).isEqualTo(NOW);
        assertThat(first.deliveredAt()).isEmpty();
        assertThat(first.buyer()).isEqualTo(new ExportedOrder.Buyer("Ana María", "Pérez", "ana@example.com",
            "3425551234", "12345678"));
        assertThat(first.address()).isEqualTo(ADDRESS);
        assertThat(first.province()).isEqualTo(Province.SANTA_FE);
        assertThat(first.lines()).isEqualTo(LINES);
        assertThat(first.amounts()).isEqualTo(newer.amounts());
        assertThat(first.couponCode()).isEmpty();
        assertThat(first.paymentProvider()).isEqualTo("MERCADOPAGO");
        assertThat(first.shipmentTrackingNumber()).isEmpty();
    }

    @Test
    void givenACustomerWithoutOrders_whenExporting_thenItGetsNone() {
        UserId ana = customer("ana@example.com");
        placed(customer("bruno@example.com"), NOW);
        persistAndForget();

        assertThat(subject.all(ana)).isEmpty();
    }

    protected NewOrder placed(UserId customer, Instant at) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), token.hash(), Optional.of(customer),
            new BuyerDetails("Ana María", "Pérez", "ana@example.com", "3425551234", "12345678"), ADDRESS,
            Province.SANTA_FE, LINES, OrderAmounts.of(Money.of(5_500_000), Money.zero(), Money.of(300_000)),
            Optional.empty(), PaymentProvider.MERCADOPAGO, "2026-10", at, StockHold.expiryOf(at));
        subject.insert(order);
        return order;
    }
}
