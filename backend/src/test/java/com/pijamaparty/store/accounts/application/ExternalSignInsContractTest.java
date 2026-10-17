package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link ExternalSignIns} (UC-27): it runs the block and hands back what it returns or throws. */
public abstract class ExternalSignInsContractTest<T extends ExternalSignIns> extends ContractTest<T> {

    @Test
    void givenABlock_whenSigningIn_thenItRunsAndItsResultIsReturned() {
        assertThat(subject.signingIn(() -> "signed in")).isEqualTo("signed in");
    }

    @Test
    void givenABlockThatFails_whenSigningIn_thenTheFailureReachesTheCaller() {
        assertThatThrownBy(() -> subject.signingIn(() -> {
            throw new IllegalStateException("the provider account was taken");
        })).hasMessageContaining("the provider account was taken");
    }
}
