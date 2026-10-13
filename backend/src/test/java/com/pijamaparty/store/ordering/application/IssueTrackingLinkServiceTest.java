package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IssueTrackingLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final List<String> TOKENS = List.of("email-token-one-0123456789abcdefghijklmnopq",
        "email-token-two-0123456789abcdefghijklmnopq");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final AtomicInteger minted = new AtomicInteger();
    private final IssueTrackingLinkService service = new IssueTrackingLinkService(
        () -> TOKENS.get(minted.getAndIncrement()), orders, Clock.fixed(NOW, ZoneOffset.UTC));
    private final OrderId order = OrderId.of(UUID.randomUUID());

    @Test
    void givenAPaidOrder_whenALinkIsIssued_thenTheNewTokenIsAnsweredAndOnlyItsHashKeptForTheOrder() {
        orders.add(order, OrderStatus.PAID, NOW);

        TrackingToken token = service.issue(order.value());

        assertThat(token.value()).isEqualTo(TOKENS.getFirst());
        assertThat(orders.linkedOrder(new TrackingToken(TOKENS.getFirst()).hash())).contains(order);
    }

    @Test
    void givenAnOrderWithALink_whenAnotherIsIssued_thenBothOpenIt() {
        orders.add(order, OrderStatus.PAID, NOW);

        TrackingToken first = service.issue(order.value());
        TrackingToken second = service.issue(order.value());

        assertThat(first).isNotEqualTo(second);
        assertThat(orders.linkedOrder(first.hash())).contains(order);
        assertThat(orders.linkedOrder(second.hash())).contains(order);
    }

    @Test
    void givenNoSuchOrder_whenALinkIsIssued_thenItFails() {
        assertThatThrownBy(() -> service.issue(order.value())).isInstanceOf(IllegalArgumentException.class);
    }
}
