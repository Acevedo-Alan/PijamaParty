package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** UC-17 (T-E3-23, decided): what correcting the buyer's email does, by the order's status. */
class EmailCorrectionTest {

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "AWAITING_PAYMENT")
    void givenAnOrderAwaitingPayment_whenItsEmailIsCorrected_thenTheConfirmationIsNotSentYet(OrderStatus status) {
        assertThat(EmailCorrection.of(status)).isEqualTo(EmailCorrection.BEFORE_CONFIRMATION);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING", "SHIPPED", "DELIVERED"})
    void givenAPaidOrder_whenItsEmailIsCorrected_thenTheConfirmationIsSentAgain(OrderStatus status) {
        assertThat(EmailCorrection.of(status)).isEqualTo(EmailCorrection.RESEND_CONFIRMATION);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "EXPIRED"})
    void givenAClosedOrder_whenItsEmailIsCorrected_thenItIsNot(OrderStatus status) {
        assertThat(EmailCorrection.of(status)).isEqualTo(EmailCorrection.CLOSED);
    }
}
