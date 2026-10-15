package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link WithdrawalRequests} (UC-29, T-E3-26): only a received request makes an order being withdrawn. */
public abstract class WithdrawalRequestsContractTest<T extends WithdrawalRequests> extends ContractTest<T> {

    protected static final Instant REQUESTED_AT = Instant.parse("2026-10-08T15:00:00Z");

    /** A paid order whose buyer sent the withdrawal form: {@code code}, out of term or not, at REQUESTED_AT. */
    protected abstract UUID givenRequested(String code, boolean outOfTerm);

    /** A paid order whose withdrawal request the administrator resolved. */
    protected abstract UUID givenResolved(String code);

    /** A paid order nobody asked to withdraw. */
    protected abstract UUID givenNoRequest();

    @Test
    void givenAReceivedRequest_whenAskedAbout_thenItIsTheActiveOne() {
        UUID order = givenRequested("ARR-7K2M9QXZ", true);

        assertThat(subject.activeOf(order)).hasValueSatisfying(withdrawal -> {
            assertThat(withdrawal.code()).isEqualTo("ARR-7K2M9QXZ");
            assertThat(withdrawal.outOfTerm()).isTrue();
            assertThat(withdrawal.requestedAt()).isEqualTo(REQUESTED_AT);
        });
    }

    @Test
    void givenAResolvedRequestOrNone_whenAskedAbout_thenThereIsNoActiveOne() {
        assertThat(subject.activeOf(givenResolved("ARR-22222222"))).isEmpty();
        assertThat(subject.activeOf(givenNoRequest())).isEmpty();
        assertThat(subject.activeOf(UUID.randomUUID())).isEmpty();
    }
}
