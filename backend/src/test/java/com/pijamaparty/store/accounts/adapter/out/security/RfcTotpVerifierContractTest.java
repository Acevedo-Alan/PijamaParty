package com.pijamaparty.store.accounts.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.TotpVerifierContractTest;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import org.junit.jupiter.api.Test;

class RfcTotpVerifierContractTest extends TotpVerifierContractTest<RfcTotpVerifier> {

    @Override
    protected RfcTotpVerifier createSubject() {
        return new RfcTotpVerifier();
    }

    @Override
    protected String codeAt(TotpSecret secret, long step) {
        return subject.codeAt(secret, step);
    }

    /** RFC 6238 appendix B, SHA-1 at T = 59 s: 94287082, whose last 6 digits any authenticator app shows. */
    @Test
    void givenTheRfc6238TestVector_whenComputed_thenTheCodeIsTheRfcOne() {
        // The RFC's ASCII key "12345678901234567890" in base32.
        TotpSecret rfcKey = new TotpSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

        assertThat(subject.codeAt(rfcKey, 59 / 30)).isEqualTo("287082");
        assertThat(subject.codeAt(rfcKey, 1_111_111_109L / 30)).isEqualTo("081804");
    }
}
