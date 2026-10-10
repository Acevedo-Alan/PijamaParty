package com.pijamaparty.store.accounts.adapter.out.security;

import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt with cost 12 (UC-04). Hashes carry the {@code {bcrypt}} prefix, so another algorithm can be added later
 * and old hashes keep working.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    static final int COST = 12;

    private final PasswordEncoder encoder;
    private final PasswordHash decoy;

    BCryptPasswordHasher() {
        var random = new SecureRandom();
        this.encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(COST, random)));
        var unguessable = new byte[32];
        random.nextBytes(unguessable);
        this.decoy = new PasswordHash(encoder.encode(HexFormat.of().formatHex(unguessable)));
    }

    @Override
    public PasswordHash hash(String password) {
        return new PasswordHash(encoder.encode(password));
    }

    @Override
    public boolean matches(String password, PasswordHash hash) {
        if (password.getBytes(StandardCharsets.UTF_8).length > PasswordRules.MAX_BYTES) {
            // BCrypt refuses such input; no stored password is that long. Same cost as a real check.
            encoder.matches("", decoy.value());
            return false;
        }
        return encoder.matches(password, hash.value());
    }

    @Override
    public PasswordHash decoy() {
        return decoy;
    }
}
