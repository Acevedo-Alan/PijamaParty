package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link OrderDetails} (UC-18): an order with what the administrator may move it to. */
public abstract class OrderDetailsContractTest<T extends OrderDetails> extends ContractTest<T> {

    /** A guest's order awaiting payment, numbered {@code number}. */
    protected abstract void givenOrderAwaitingPayment(UUID orderId, long number);

    @Test
    void givenAnOrderAwaitingPayment_whenFound_thenItCanOnlyBeCancelled() {
        UUID order = UUID.randomUUID();
        givenOrderAwaitingPayment(order, 4242);

        assertThat(subject.find(order)).hasValueSatisfying(found -> {
            assertThat(found.orderId()).isEqualTo(order);
            assertThat(found.number()).isEqualTo(4242);
            assertThat(found.status()).isEqualTo(Status.AWAITING_PAYMENT);
            assertThat(found.nextStatuses()).isEqualTo(List.of(Status.CANCELLED));
        });
    }

    @Test
    void givenNoSuchOrder_whenFound_thenThereIsNone() {
        assertThat(subject.find(UUID.randomUUID())).isEmpty();
    }
}
