package com.pijamaparty.store.ordering.adapter.out.random;

import com.pijamaparty.store.ordering.application.WithdrawalCodes;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** UC-29 (T-E3-25): each character of the code drawn uniformly from {@link WithdrawalCode#ALPHABET}. */
@Component
class SecureRandomWithdrawalCodes implements WithdrawalCodes {

    private final SecureRandom random = new SecureRandom();

    @Override
    public WithdrawalCode next() {
        var code = new StringBuilder(WithdrawalCode.PREFIX);
        for (int i = 0; i < WithdrawalCode.LENGTH; i++) {
            code.append(WithdrawalCode.ALPHABET.charAt(random.nextInt(WithdrawalCode.ALPHABET.length())));
        }
        return new WithdrawalCode(code.toString());
    }
}
