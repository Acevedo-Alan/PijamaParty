package com.pijamaparty.store.platform.adapter.out;

import com.pijamaparty.store.platform.application.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Random (version 4) UUIDs from {@code SecureRandom}. */
@Component
class RandomIdGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
