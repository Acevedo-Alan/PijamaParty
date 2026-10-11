package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import java.util.Optional;

/** Outbound port: encrypts the authenticator secret at rest and in the pending sign-in (T-E1-16). */
public interface SecondFactorCipher {

    EncryptedSecret encrypt(TotpSecret secret);

    /** Empty when the bytes were not made by this cipher with its key, or were changed. */
    Optional<TotpSecret> decrypt(EncryptedSecret secret);
}
