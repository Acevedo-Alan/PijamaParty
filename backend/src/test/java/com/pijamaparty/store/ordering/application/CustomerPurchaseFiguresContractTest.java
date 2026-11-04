package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.CustomerPurchaseFigures.Figures;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerPurchaseFigures} (UC-34, T-E8-11): every linked order counts as an order; only those paid
 * and neither cancelled nor expired count as purchases, add to the amount spent and date the last purchase; a guest's
 * or another customer's order never counts.
 */
public abstract class CustomerPurchaseFiguresContractTest<T extends CustomerPurchaseFigures> extends ContractTest<T> {

    protected static final Instant T0 = Instant.parse("2026-10-15T15:00:00Z");

    /** An order in {@code status} of {@code totalCents}, placed at {@code at}, linked to {@code customer} or a guest's. */
    protected abstract void givenOrder(Optional<UUID> customer, String status, long totalCents, Instant at);

    @Test
    void givenADeliveredACancelledAndAnAwaitingOrder_whenFigured_thenThreeOrdersOnePurchaseAndItsTotal() {
        UUID ana = UUID.randomUUID();
        givenOrder(Optional.of(ana), "DELIVERED", 3_000_000, T0);
        givenOrder(Optional.of(ana), "CANCELLED", 1_000_000, T0.plusSeconds(60));
        givenOrder(Optional.of(ana), "AWAITING_PAYMENT", 500_000, T0.plusSeconds(120));
        givenOrder(Optional.empty(), "PAID", 700_000, T0.plusSeconds(180));
        givenOrder(Optional.of(UUID.randomUUID()), "PAID", 900_000, T0.plusSeconds(240));
        persistAndForget();

        assertThat(subject.figuresOf(UserId.of(ana)))
            .isEqualTo(new Figures(3, 1, Money.of(3_000_000), Optional.of(T0)));
    }

    @Test
    void givenEveryBoughtStatus_whenFigured_thenEachCountsAndTheNewestDatesTheLastPurchase() {
        UUID ana = UUID.randomUUID();
        givenOrder(Optional.of(ana), "PAID", 100, T0);
        givenOrder(Optional.of(ana), "PREPARING", 200, T0.plusSeconds(60));
        givenOrder(Optional.of(ana), "SHIPPED", 300, T0.plusSeconds(120));
        givenOrder(Optional.of(ana), "DELIVERED", 400, T0.plusSeconds(30));
        givenOrder(Optional.of(ana), "EXPIRED", 500, T0.plusSeconds(600));
        persistAndForget();

        assertThat(subject.figuresOf(UserId.of(ana)))
            .isEqualTo(new Figures(5, 4, Money.of(1_000), Optional.of(T0.plusSeconds(120))));
    }

    @Test
    void givenACustomerWithoutOrders_whenFigured_thenAllIsZero() {
        assertThat(subject.figuresOf(UserId.of(UUID.randomUUID()))).isEqualTo(Figures.NONE);
    }
}
