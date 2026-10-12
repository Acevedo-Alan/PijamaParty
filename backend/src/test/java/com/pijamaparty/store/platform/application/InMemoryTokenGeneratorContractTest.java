package com.pijamaparty.store.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryTokenGeneratorContractTest extends TokenGeneratorContractTest {

    @Override
    protected TokenGenerator createSubject() {
        return new InMemoryTokenGenerator();
    }

    @Test
    void givenTheFake_whenAskingForTokens_thenTheyArePredictable() {
        assertThat(subject.newToken()).isEqualTo("A".repeat(42) + "1");
        assertThat(subject.newToken()).isEqualTo(InMemoryTokenGenerator.nth(2));
    }
}
