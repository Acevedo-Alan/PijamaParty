package com.pijamaparty.store.accounts.adapter.out.security;

import com.pijamaparty.store.accounts.application.RecoveryCodeGenerator;
import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** {@value RecoveryCodeGenerator#COUNT} codes of 10 characters (50 bits each) from {@link SecureRandom}. */
@Component
class RandomRecoveryCodeGenerator implements RecoveryCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public List<SecondFactorCode.Recovery> newCodes() {
        Set<String> codes = new LinkedHashSet<>();
        while (codes.size() < COUNT) {
            var code = new StringBuilder(SecondFactorCode.RECOVERY_LENGTH);
            for (int i = 0; i < SecondFactorCode.RECOVERY_LENGTH; i++) {
                code.append(SecondFactorCode.RECOVERY_ALPHABET.charAt(
                    random.nextInt(SecondFactorCode.RECOVERY_ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes.stream().map(SecondFactorCode.Recovery::new).toList();
    }
}
