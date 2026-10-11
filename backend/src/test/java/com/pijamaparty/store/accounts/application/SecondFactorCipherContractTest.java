package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Contract of {@link SecondFactorCipher}: what it encrypts it gives back, and nothing else. */
public abstract class SecondFactorCipherContractTest<T extends SecondFactorCipher> extends ContractTest<T> {

    private static final TotpSecret SECRET = new TotpSecret("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");

    @Test
    void givenASecret_whenEncryptedAndDecrypted_thenItComesBackAndIsNotStoredInPlain() {
        EncryptedSecret sealed = subject.encrypt(SECRET);

        assertThat(subject.decrypt(sealed)).contains(SECRET);
        assertThat(new String(sealed.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain(SECRET.base32());
    }

    @Test
    void givenChangedOrForeignBytes_whenDecrypted_thenNothingComesBack() {
        byte[] changed = subject.encrypt(SECRET).bytes();
        changed[0] ^= 0x7f;

        assertThat(subject.decrypt(new EncryptedSecret(changed))).isEmpty();
        assertThat(subject.decrypt(new EncryptedSecret(new byte[] {9, 9, 9}))).isEmpty();
    }
}
