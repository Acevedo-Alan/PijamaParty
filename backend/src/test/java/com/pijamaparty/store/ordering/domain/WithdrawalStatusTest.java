package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WithdrawalStatusTest {

    @Test
    void givenAReceivedRequest_whenAskedWhetherItMayBeResolved_thenItMay() {
        assertThat(WithdrawalStatus.RECEIVED.isResolvable()).isTrue();
    }

    @Test
    void givenAResolvedRequest_whenAskedWhetherItMayBeResolved_thenItMayNot() {
        assertThat(WithdrawalStatus.RESOLVED.isResolvable()).isFalse();
    }
}
