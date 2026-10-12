package com.pijamaparty.store.platform.adapter.out;

import com.pijamaparty.store.platform.application.TokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 32 bytes from {@link SecureRandom} (256 bits, RNF-29.6) in unpadded base64url. */
@Component
class SecureRandomTokenGenerator implements TokenGenerator {

    private static final int BYTES = 32;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();

    @Override
    public String newToken() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }
}
