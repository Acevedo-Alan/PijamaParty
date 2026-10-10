package com.pijamaparty.store.catalog.adapter.out.sku;

import com.pijamaparty.store.catalog.application.SkuGenerator;
import com.pijamaparty.store.catalog.domain.Sku;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * UC-06: {@code PP-} and 8 random characters of Crockford's base32 (digits and capitals without I, L, O and U, which
 * look like 1, 0 or V), about 10^12 codes, so a clash is rare and retried by the use case.
 */
@Component
class RandomSkuGenerator implements SkuGenerator {

    static final String PREFIX = "PP-";
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int LENGTH = 8;

    private final RandomGenerator random;

    @Autowired
    RandomSkuGenerator() {
        this(new SecureRandom());
    }

    RandomSkuGenerator(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public Sku newSku() {
        StringBuilder sku = new StringBuilder(PREFIX.length() + LENGTH).append(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            sku.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new Sku(sku.toString());
    }
}
