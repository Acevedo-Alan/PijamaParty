package com.pijamaparty.store.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CheckoutAttemptTest {

    @Test
    void givenAnOrderNumberAndAnAttempt_whenBuildingIt_thenItsReferenceIsTheOrderNumberAndTheAttempt() {
        assertThat(CheckoutAttempt.of(1001, 2)).isEqualTo(new CheckoutAttempt(2, "1001-2"));
    }

    @Test
    void givenANumberThatIsNotPositive_whenBuildingIt_thenItIsRejected() {
        assertThatThrownBy(() -> CheckoutAttempt.of(1001, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckoutAttempt.of(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
