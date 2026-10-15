package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** UC-29 (T-E3-25): 10 calendar days, in Argentina's time zone, from the later of the purchase and the delivery. */
class WithdrawalTermTest {

    // 2026-10-01 10:00 in Argentina (UTC-3).
    private static final Instant PURCHASED = Instant.parse("2026-10-01T13:00:00Z");
    // 2026-10-05 23:30 in Argentina, already 2026-10-06 in UTC.
    private static final Instant DELIVERED = Instant.parse("2026-10-06T02:30:00Z");

    @Test
    void givenAnOrderNotDeliveredYet_whenRequestedLongAfterThePurchase_thenItIsInTerm() {
        assertThat(WithdrawalTerm.isOutOfTerm(PURCHASED, Optional.empty(), Instant.parse("2027-01-01T12:00:00Z")))
            .isFalse();
    }

    @Test
    void givenADeliveredOrder_whenRequestedOnTheLastMinuteOfThe10thDayAfterTheDeliveryInArgentina_thenItIsInTerm() {
        // 2026-10-15 23:59 in Argentina: the 10th day after 2026-10-05.
        assertThat(WithdrawalTerm.isOutOfTerm(PURCHASED, Optional.of(DELIVERED),
            Instant.parse("2026-10-16T02:59:00Z"))).isFalse();
    }

    @Test
    void givenAnOrderDeliveredMoreThan10DaysAgo_whenRequested_thenItIsOutOfTerm() {
        // 2026-10-16 00:00 in Argentina.
        assertThat(WithdrawalTerm.isOutOfTerm(PURCHASED, Optional.of(DELIVERED),
            Instant.parse("2026-10-16T03:00:00Z"))).isTrue();
    }

    @Test
    void givenADeliveryRecordedBeforeThePurchase_whenRequested_thenThePurchaseCountsAsTheLater() {
        Instant purchasedLater = Instant.parse("2026-10-10T13:00:00Z");

        assertThat(WithdrawalTerm.isOutOfTerm(purchasedLater, Optional.of(DELIVERED),
            Instant.parse("2026-10-20T13:00:00Z"))).isFalse();
        assertThat(WithdrawalTerm.isOutOfTerm(purchasedLater, Optional.of(DELIVERED),
            Instant.parse("2026-10-21T13:00:00Z"))).isTrue();
    }
}
