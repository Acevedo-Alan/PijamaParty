package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link PasswordHasher}. */
public abstract class PasswordHasherContractTest<T extends PasswordHasher> extends ContractTest<T> {

    @Test
    void givenAHashedPassword_whenCheckingIt_thenOnlyThatPasswordMatches() {
        var hash = subject.hash("correct horse battery");

        assertThat(hash.value()).doesNotContain("correct horse battery");
        assertThat(subject.matches("correct horse battery", hash)).isTrue();
        assertThat(subject.matches("Correct horse battery", hash)).isFalse();
    }

    @Test
    void givenTheDecoy_whenCheckingAnyPassword_thenNothingMatches() {
        assertThat(subject.matches("", subject.decoy())).isFalse();
        assertThat(subject.matches("correct horse battery", subject.decoy())).isFalse();
    }

    @Test
    void givenAPasswordLongerThanAnyAllowed_whenChecked_thenItDoesNotMatchAndDoesNotFail() {
        var hash = subject.hash("correct horse battery");

        assertThat(subject.matches("x".repeat(1024), hash)).isFalse();
    }
}
