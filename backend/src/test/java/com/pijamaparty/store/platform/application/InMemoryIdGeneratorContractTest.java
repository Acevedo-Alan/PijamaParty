package com.pijamaparty.store.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryIdGeneratorContractTest extends IdGeneratorContractTest {

    @Override
    protected IdGenerator createSubject() {
        return new InMemoryIdGenerator();
    }

    @Test
    void givenTheFake_whenAskingForIds_thenTheyArePredictable() {
        assertThat(subject.newId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(subject.newId()).isEqualTo(InMemoryIdGenerator.nth(2));
    }
}
