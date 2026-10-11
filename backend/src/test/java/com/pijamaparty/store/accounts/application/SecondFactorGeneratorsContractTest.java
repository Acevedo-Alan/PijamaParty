package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contracts of {@link TotpSecretGenerator} and {@link RecoveryCodeGenerator}: valid values, never repeated. */
public final class SecondFactorGeneratorsContractTest {

    private SecondFactorGeneratorsContractTest() {
    }

    public abstract static class Secrets<T extends TotpSecretGenerator> extends ContractTest<T> {

        @Test
        void givenSeveralSecrets_whenGenerated_thenEachHasAtLeast160BitsAndNoneRepeats() {
            Set<String> secrets = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                TotpSecret secret = subject.newSecret();
                assertThat(secret.base32()).hasSizeGreaterThanOrEqualTo(32);
                secrets.add(secret.base32());
            }
            assertThat(secrets).hasSize(20);
        }
    }

    public abstract static class RecoveryCodes<T extends RecoveryCodeGenerator> extends ContractTest<T> {

        @Test
        void givenACall_whenGenerating_then10DifferentCodesThatReadBackComeOut() {
            List<SecondFactorCode.Recovery> codes = subject.newCodes();

            assertThat(codes).hasSize(RecoveryCodeGenerator.COUNT).doesNotHaveDuplicates();
            codes.forEach(code -> assertThat(SecondFactorCode.parse(code.display().toLowerCase())).contains(code));
            assertThat(subject.newCodes()).doesNotContainAnyElementsOf(codes);
        }
    }
}
