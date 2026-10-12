package com.pijamaparty.store.platform.adapter.out;

import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.application.TokenGeneratorContractTest;

class SecureRandomTokenGeneratorContractTest extends TokenGeneratorContractTest {

    @Override
    protected TokenGenerator createSubject() {
        return new SecureRandomTokenGenerator();
    }
}
