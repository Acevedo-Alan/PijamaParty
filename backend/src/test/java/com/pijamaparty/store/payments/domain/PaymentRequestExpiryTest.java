package com.pijamaparty.store.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** UC-14 (T-E3b-02): a MODO payment request expires within 10 minutes, never after the reservation, save MODO's 5. */
class PaymentRequestExpiryTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

    @Test
    void givenAReservationWithMoreThanTenMinutesLeft_whenAPaymentRequestIsCreated_thenItExpiresInTenMinutes() {
        assertThat(PaymentRequestExpiry.of(NOW, NOW.plusSeconds(1800))).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void givenAReservationEndingWithinTenMinutes_whenAPaymentRequestIsCreated_thenItExpiresWithTheReservation() {
        assertThat(PaymentRequestExpiry.of(NOW, NOW.plusSeconds(420))).isEqualTo(NOW.plusSeconds(420));
    }

    @Test
    void givenAReservationEndingWithinFiveMinutes_whenAPaymentRequestIsCreated_thenItExpiresAtMODOsMinimum() {
        assertThat(PaymentRequestExpiry.of(NOW, NOW.plusSeconds(90))).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void givenAFractionOfASecond_whenAPaymentRequestIsCreated_thenItsExpiryIsInWholeSecondsWithinTheRule() {
        Instant now = NOW.plusMillis(400);

        assertThat(PaymentRequestExpiry.of(now, now.plusSeconds(1800))).isEqualTo(NOW.plusSeconds(600));
        assertThat(PaymentRequestExpiry.of(now, now.plusSeconds(60))).isEqualTo(NOW.plusSeconds(301));
        assertThat(PaymentRequestExpiry.of(now, NOW.plusMillis(300_600))).isEqualTo(NOW.plusSeconds(301));
    }
}
