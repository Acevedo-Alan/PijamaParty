package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link CustomerAccountChanges} (UC-27): it runs the block and hands back what it returns or throws. */
public abstract class CustomerAccountChangesContractTest<T extends CustomerAccountChanges> extends ContractTest<T> {

    @Test
    void givenABlock_whenChanging_thenItRunsAndItsResultIsReturned() {
        assertThat(subject.changing(() -> "changed")).isEqualTo("changed");
    }

    @Test
    void givenABlockThatFails_whenChanging_thenTheFailureReachesTheCaller() {
        assertThatThrownBy(() -> subject.changing(() -> {
            throw new IllegalStateException("the account was deleted");
        })).hasMessageContaining("the account was deleted");
    }
}
