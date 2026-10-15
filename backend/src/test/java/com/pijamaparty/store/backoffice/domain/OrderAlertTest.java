package com.pijamaparty.store.backoffice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.domain.OrderAlert.BuyerEmailProblem;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderAlertTest {

    @Test
    void givenAnOrderWithoutProblems_whenItsAlertsAreAskedFor_thenThereAreNone() {
        assertThat(OrderAlert.of(false, Optional.empty())).isEmpty();
    }

    @Test
    void givenAPaymentAfterClosingAndAFailedEmail_whenItsAlertsAreAskedFor_thenTheRefundComesFirst() {
        assertThat(OrderAlert.of(true, Optional.of(BuyerEmailProblem.FAILED)))
            .containsExactly(OrderAlert.PAID_AFTER_CLOSING, OrderAlert.BUYER_EMAIL_FAILED);
    }

    @Test
    void givenOnlyABouncedEmail_whenItsAlertsAreAskedFor_thenTheAdministratorIsToldItBounced() {
        assertThat(OrderAlert.of(false, Optional.of(BuyerEmailProblem.BOUNCED)))
            .containsExactly(OrderAlert.BUYER_EMAIL_BOUNCED);
    }

    @Test
    void givenAPaymentAfterClosingAndABouncedEmail_whenItsAlertsAreAskedFor_thenTheRefundComesFirst() {
        assertThat(OrderAlert.of(true, Optional.of(BuyerEmailProblem.BOUNCED)))
            .containsExactly(OrderAlert.PAID_AFTER_CLOSING, OrderAlert.BUYER_EMAIL_BOUNCED);
    }

    @Test
    void givenOnlyAFailedEmail_whenItsAlertsAreAskedFor_thenTheAdministratorIsToldToCallTheBuyer() {
        assertThat(OrderAlert.of(false, Optional.of(BuyerEmailProblem.FAILED)))
            .containsExactly(OrderAlert.BUYER_EMAIL_FAILED);
    }
}
