package com.pijamaparty.store.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyKeysContractTest extends IdempotencyKeysContractTest<InMemoryIdempotencyKeys> {

    @Override
    protected InMemoryIdempotencyKeys createSubject() {
        return new InMemoryIdempotencyKeys();
    }

    @Test
    void givenAKeyHeldElsewhere_whenClaimingIt_thenItIsBusy() {
        UUID key = UUID.randomUUID();
        subject.holdElsewhere(GUEST, key);

        assertThat(subject.claim(GUEST, key)).isEqualTo(new IdempotencyKeys.Claim.Busy());
        assertThat(subject.claim(CUSTOMER, key)).isEqualTo(new IdempotencyKeys.Claim.Fresh());
    }
}
