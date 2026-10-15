package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.OrderEmailCorrected;
import com.pijamaparty.store.ordering.api.OrderPaid;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link OrderEvents}: each call publishes exactly its event of {@code ordering.api}, at once. */
public abstract class OrderEventsContractTest<T extends OrderEvents> extends ContractTest<T> {

    /** What the subject published so far, in order. */
    protected abstract List<Object> published();

    @Test
    void givenAnOrderThatBecamePaid_whenPublished_thenOrderPaidCarriesItsIdAndNumber() {
        OrderId order = OrderId.of(UUID.randomUUID());

        subject.orderPaid(order, 1001);

        assertThat(published()).containsExactly(new OrderPaid(order.value(), 1001));
    }

    @Test
    void givenTwoPaidOrders_whenPublished_thenEachHasItsOwnEventInOrder() {
        OrderId first = OrderId.of(UUID.randomUUID());
        OrderId second = OrderId.of(UUID.randomUUID());

        subject.orderPaid(first, 1001);
        subject.orderPaid(second, 1002);

        assertThat(published()).containsExactly(new OrderPaid(first.value(), 1001),
            new OrderPaid(second.value(), 1002));
    }

    @Test
    void givenAPaidOrderWhoseEmailWasCorrected_whenPublished_thenOrderEmailCorrectedCarriesItsIdAndNumber() {
        OrderId order = OrderId.of(UUID.randomUUID());

        subject.orderEmailCorrected(order, 1001);

        assertThat(published()).containsExactly(new OrderEmailCorrected(order.value(), 1001));
    }
}
