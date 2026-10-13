package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R-01, R-02, UC-16: the amounts, lines and stock hold of a new order. */
class OrderAmountsTest {

    @Test
    void givenSubtotalDiscountAndShipping_whenCalculatingTheTotal_thenItIsTheSubtotalLessTheDiscountPlusShipping() {
        var amounts = OrderAmounts.of(Money.of(5_000_000), Money.of(500_000), Money.of(300_000));

        assertThat(amounts.total()).isEqualTo(Money.of(4_800_000));
    }

    @Test
    void givenInconsistentAmounts_whenBuildingThem_thenItFails() {
        assertThatThrownBy(() -> OrderAmounts.of(Money.zero(), Money.zero(), Money.of(300_000)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderAmounts.of(Money.of(100), Money.of(101), Money.zero()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderAmounts(Money.of(100), Money.zero(), Money.zero(), Money.of(99)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenALine_whenAskedForItsAmount_thenItIsTheUnitPriceTimesTheQuantity() {
        var line = new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 3);

        assertThat(line.amount()).isEqualTo(Money.of(6_000_000));
        assertThatThrownBy(() -> new OrderLine(UUID.randomUUID(), "PL", "Pijama", "M", "Rosa", Money.of(1), 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(UUID.randomUUID(), "PL", "Pijama", "M", "Rosa", Money.zero(), 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnOrderPlacedNow_whenItsStockHoldExpires_thenItIs30MinutesLater() {
        assertThat(StockHold.expiryOf(Instant.parse("2026-10-12T15:00:00Z")))
            .isEqualTo(Instant.parse("2026-10-12T15:30:00Z"));
    }
}
