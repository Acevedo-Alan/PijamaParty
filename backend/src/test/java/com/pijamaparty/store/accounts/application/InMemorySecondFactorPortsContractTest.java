package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryEnrollmentQrCode;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryRecoveryCodeGenerator;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryRecoveryCodes;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemorySecondFactorCipher;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryTotpSecretGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Nested;

/** The second-factor fakes run the same contracts as the real adapters. */
class InMemorySecondFactorPortsContractTest {

    @Nested
    class Cipher extends SecondFactorCipherContractTest<InMemorySecondFactorCipher> {

        @Override
        protected InMemorySecondFactorCipher createSubject() {
            return new InMemorySecondFactorCipher();
        }
    }

    @Nested
    class Secrets extends SecondFactorGeneratorsContractTest.Secrets<InMemoryTotpSecretGenerator> {

        @Override
        protected InMemoryTotpSecretGenerator createSubject() {
            return new InMemoryTotpSecretGenerator();
        }
    }

    @Nested
    class Codes extends SecondFactorGeneratorsContractTest.RecoveryCodes<InMemoryRecoveryCodeGenerator> {

        @Override
        protected InMemoryRecoveryCodeGenerator createSubject() {
            return new InMemoryRecoveryCodeGenerator();
        }
    }

    @Nested
    class Store extends RecoveryCodesContractTest<InMemoryRecoveryCodes> {

        @Override
        protected InMemoryRecoveryCodes createSubject() {
            return new InMemoryRecoveryCodes();
        }

        @Override
        protected UserId givenAdministrator() {
            return UserId.of(UUID.randomUUID());
        }
    }

    @Nested
    class Qr extends EnrollmentQrCodeContractTest<InMemoryEnrollmentQrCode> {

        @Override
        protected InMemoryEnrollmentQrCode createSubject() {
            return new InMemoryEnrollmentQrCode();
        }

        @Override
        protected String scan(String dataUri) {
            return dataUri.substring(dataUri.indexOf("QR(") + 3, dataUri.length() - 1);
        }
    }
}
