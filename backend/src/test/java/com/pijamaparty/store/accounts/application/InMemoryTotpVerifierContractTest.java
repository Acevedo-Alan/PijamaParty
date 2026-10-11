package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.TotpSecret;

class InMemoryTotpVerifierContractTest extends TotpVerifierContractTest<InMemoryTotpVerifier> {

    @Override
    protected InMemoryTotpVerifier createSubject() {
        return new InMemoryTotpVerifier();
    }

    @Override
    protected String codeAt(TotpSecret secret, long step) {
        return InMemoryTotpVerifier.codeAt(secret, step);
    }
}
