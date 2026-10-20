package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.OrderEmailCorrected;
import com.pijamaparty.store.ordering.application.CorrectOrderEmail.Command;
import com.pijamaparty.store.ordering.application.CorrectOrderEmail.Result;
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
import com.pijamaparty.store.shipping.api.Province;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-17 (T-E3-23): the buyer corrects the email of its order from the tracking link. */
class CorrectOrderEmailServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-13T15:00:00Z");
    private static final Instant NOW = PLACED_AT.plus(Duration.ofMinutes(20));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryOrderEvents events = new InMemoryOrderEvents();
    private final CorrectOrderEmailService service =
        new CorrectOrderEmailService(orders, orders, events, Clock.fixed(NOW, ZoneOffset.UTC));
    private final TrackingToken token = newToken();

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING", "SHIPPED", "DELIVERED"})
    void givenAPaidOrder_whenItsEmailIsCorrected_thenItIsSavedNormalizedAndTheConfirmationGoesAgain(
        OrderStatus status) {
        NewOrder order = placed(status);

        assertThat(service.execute(new Command(token, "  Ana.Perez@Gmail.com ")))
            .isEqualTo(new Result.Corrected(true));

        assertThat(orders.emailOf(order.id())).contains("ana.perez@gmail.com");
        assertThat(orders.updatedAtOf(order.id())).contains(NOW);
        assertThat(events.published()).containsExactly(new OrderEmailCorrected(order.id().value(), order.number()));
    }

    @Test
    void givenAnOrderAwaitingPayment_whenItsEmailIsCorrected_thenOnlyTheEmailChanges() {
        NewOrder order = placed(OrderStatus.AWAITING_PAYMENT);

        assertThat(service.execute(new Command(token, "ana.perez@gmail.com")))
            .isEqualTo(new Result.Corrected(false));

        assertThat(orders.emailOf(order.id())).contains("ana.perez@gmail.com");
        assertThat(events.published()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "EXPIRED"})
    void givenAClosedOrder_whenItsEmailIsCorrected_thenItIsRefusedAndNothingChanges(OrderStatus status) {
        NewOrder order = placed(status);

        assertThat(service.execute(new Command(token, "ana.perez@gmail.com"))).isEqualTo(new Result.Closed(status));

        assertThat(orders.emailOf(order.id())).contains("ana.perez@gmial.com");
        assertThat(events.published()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ana.perez", "ana perez@gmail.com", "@gmail.com", "ana@", "ana@gm@il.com"})
    void givenAnInvalidEmail_whenCorrected_thenItIsRefusedAndNothingChanges(String email) {
        NewOrder order = placed(OrderStatus.PAID);

        assertThat(service.execute(new Command(token, email))).isEqualTo(new Result.InvalidEmail());

        assertThat(orders.emailOf(order.id())).contains("ana.perez@gmial.com");
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAnEmailLongerThan254Characters_whenCorrected_thenItIsRefused() {
        placed(OrderStatus.PAID);

        assertThat(service.execute(new Command(token, "a".repeat(245) + "@gmail.com")))
            .isEqualTo(new Result.InvalidEmail());
    }

    @Test
    void givenTheSameEmailAsBefore_whenCorrectedOnAPaidOrder_thenTheConfirmationGoesAgainAnyway() {
        NewOrder order = placed(OrderStatus.PAID);

        assertThat(service.execute(new Command(token, "ana.perez@gmial.com")))
            .isEqualTo(new Result.Corrected(true));
        assertThat(events.published()).containsExactly(new OrderEmailCorrected(order.id().value(), order.number()));
    }

    @Test
    void givenATokenOfNoOrder_whenCorrected_thenItIsNotFound() {
        placed(OrderStatus.PAID);

        assertThat(service.execute(new Command(newToken(), "ana.perez@gmail.com"))).isEqualTo(new Result.NotFound());
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenTheLinkOfTheConfirmationEmail_whenCorrectedThroughIt_thenItIsThatOrder() {
        NewOrder order = placed(OrderStatus.PAID);
        TrackingToken emailed = newToken();
        orders.add(order.id(), emailed.hash(), NOW);

        assertThat(service.execute(new Command(emailed, "ana.perez@gmail.com")))
            .isEqualTo(new Result.Corrected(true));
        assertThat(orders.emailOf(order.id())).contains("ana.perez@gmail.com");
    }

    @Test
    void givenACommand_whenPrinted_thenNeitherTheTokenNorTheEmailShow() {
        assertThat(new Command(token, "ana.perez@gmail.com").toString())
            .doesNotContain(token.value()).doesNotContain("ana.perez");
    }

    /** An order of {@link #token} with a misspelled email, placed and then taken to {@code status}. */
    private NewOrder placed(OrderStatus status) {
        NewOrder order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(), token.hash(), Optional.empty(),
            new BuyerDetails("Ana", "Pérez", "ana.perez@gmial.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "3000"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 1)),
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", PLACED_AT, StockHold.expiryOf(PLACED_AT));
        orders.insert(order);
        List<OrderStatus> path = switch (status) {
            case AWAITING_PAYMENT -> List.of();
            case PAID -> List.of(OrderStatus.PAID);
            case PREPARING -> List.of(OrderStatus.PAID, OrderStatus.PREPARING);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
            case DELIVERED ->
                List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            case CANCELLED -> List.of(OrderStatus.CANCELLED);
            case EXPIRED -> List.of(OrderStatus.EXPIRED);
        };
        OrderStatus current = OrderStatus.AWAITING_PAYMENT;
        for (OrderStatus next : path) {
            orders.transition(order.id(), current, next, PLACED_AT);
            current = next;
        }
        return order;
    }

    private static TrackingToken newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
