package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderPayabilityTest {

    private static final Instant EXPIRY = Instant.parse("2026-10-13T15:30:00Z");

    @Test
    void givenAnOrderAwaitingPayment_whenItsReservationIsStillOn_thenItIsPayableUntilTheExpiry() {
        assertThat(OrderPayability.isPayable(OrderStatus.AWAITING_PAYMENT, EXPIRY, EXPIRY.minusNanos(1))).isTrue();
        assertThat(OrderPayability.isPayable(OrderStatus.AWAITING_PAYMENT, EXPIRY, EXPIRY)).isFalse();
    }

    @Test
    void givenAnOrderNoLongerAwaitingPayment_whenItsReservationIsStillOn_thenItIsNotPayable() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status != OrderStatus.AWAITING_PAYMENT) {
                assertThat(OrderPayability.isPayable(status, EXPIRY, EXPIRY.minusSeconds(60))).as(status.name())
                    .isFalse();
            }
        }
    }
}
