package com.pijamaparty.store.accounts.adapter.out.security;

import com.pijamaparty.store.accounts.application.SecondFactorCipher;
import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM with the key {@code store.admin.second-factor-key} (base64 of 32 bytes, a Docker secret in production).
 * Layout: version (1 byte), IV (12 bytes), ciphertext with its 16-byte tag. Without a valid key the API does not start.
 */
@Component
class AesGcmSecondFactorCipher implements SecondFactorCipher {

    private static final byte VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** Binds the bytes to this use, so they cannot be taken for another encrypted value. */
    private static final byte[] CONTEXT = "pijamaparty:totp-secret:v1".getBytes(StandardCharsets.US_ASCII);

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    AesGcmSecondFactorCipher(@Value("${store.admin.second-factor-key:}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("store.admin.second-factor-key (SECOND_FACTOR_KEY) is not base64", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                "store.admin.second-factor-key (SECOND_FACTOR_KEY) must be the base64 of 32 random bytes");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public EncryptedSecret encrypt(TotpSecret secret) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(CONTEXT);
            byte[] sealed = cipher.doFinal(secret.base32().getBytes(StandardCharsets.US_ASCII));
            return new EncryptedSecret(ByteBuffer.allocate(1 + IV_BYTES + sealed.length)
                .put(VERSION).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot encrypt the second factor", e);
        }
    }

    @Override
    public Optional<TotpSecret> decrypt(EncryptedSecret secret) {
        byte[] bytes = secret.bytes();
        if (bytes.length <= 1 + IV_BYTES || bytes[0] != VERSION) {
            return Optional.empty();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(bytes, 1, 1 + IV_BYTES)));
            cipher.updateAAD(CONTEXT);
            byte[] plain = cipher.doFinal(bytes, 1 + IV_BYTES, bytes.length - 1 - IV_BYTES);
            return Optional.of(new TotpSecret(new String(plain, StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Another key, or changed bytes: GCM refuses them.
            return Optional.empty();
        }
    }
}
