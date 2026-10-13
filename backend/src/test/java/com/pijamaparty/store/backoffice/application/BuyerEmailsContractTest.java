package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.domain.OrderAlert.BuyerEmailProblem;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link BuyerEmails} (UC-18): only the buyer's email that will not be sent is a problem. */
public abstract class BuyerEmailsContractTest<T extends BuyerEmails> extends ContractTest<T> {

    /** The payment-confirmed email to the buyer of the order failed. */
    protected abstract void givenFailed(UUID orderId);

    /** The payment-confirmed email to the buyer of the order is still pending. */
    protected abstract void givenPending(UUID orderId);

    @Test
    void givenTheBuyersEmailFailed_whenAskedAbout_thenItFailed() {
        UUID order = UUID.randomUUID();
        givenFailed(order);

        assertThat(subject.problemOf(order)).contains(BuyerEmailProblem.FAILED);
    }

    @Test
    void givenTheBuyersEmailPendingOrNone_whenAskedAbout_thenThereIsNoProblem() {
        UUID order = UUID.randomUUID();
        givenPending(order);

        assertThat(subject.problemOf(order)).isEmpty();
        assertThat(subject.problemOf(UUID.randomUUID())).isEmpty();
    }
}
