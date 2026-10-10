package com.pijamaparty.store.platform.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.application.IdGeneratorContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RandomIdGeneratorContractTest extends IdGeneratorContractTest {

    @Override
    protected IdGenerator createSubject() {
        return new RandomIdGenerator();
    }

    @Test
    void givenTheProductionGenerator_whenAskingForAnId_thenItIsARandomVersion4Uuid() {
        UUID id = subject.newId();

        assertThat(id.version()).isEqualTo(4);
        assertThat(id.variant()).isEqualTo(2);
    }
}
