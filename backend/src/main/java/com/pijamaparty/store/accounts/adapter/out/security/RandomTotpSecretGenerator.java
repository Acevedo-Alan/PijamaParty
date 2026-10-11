package com.pijamaparty.store.accounts.adapter.out.security;

import com.pijamaparty.store.accounts.application.TotpSecretGenerator;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.springframework.stereotype.Component;

/** 32 base32 characters (160 bits) from the totp library's SecureRandom generator. */
@Component
class RandomTotpSecretGenerator implements TotpSecretGenerator {

    private static final int LENGTH = 32;

    private final DefaultSecretGenerator generator = new DefaultSecretGenerator(LENGTH);

    @Override
    public TotpSecret newSecret() {
        return new TotpSecret(generator.generate());
    }
}
