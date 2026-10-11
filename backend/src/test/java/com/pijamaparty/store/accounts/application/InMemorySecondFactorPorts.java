package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.RecoveryCodeHash;
import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.platform.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** Fakes of the small second-factor ports (T-E1-16). Each is its own class, grouped here for reading. */
public final class InMemorySecondFactorPorts {

    private InMemorySecondFactorPorts() {
    }

    /** Fake of {@link TotpSecretGenerator}: a different valid secret each time, in order. */
    public static class InMemoryTotpSecretGenerator implements TotpSecretGenerator {

        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        private int next;

        @Override
        public synchronized TotpSecret newSecret() {
            var secret = new StringBuilder("JBSWY3DPEHPK3PXP");
            int value = next++;
            for (int i = 0; i < 16; i++) {
                secret.append(ALPHABET.charAt(value % ALPHABET.length()));
                value /= ALPHABET.length();
            }
            return new TotpSecret(secret.toString());
        }
    }

    /**
     * Fake of {@link SecondFactorCipher}: "encrypts" by prefixing a marker and reversing the bytes, so the stored form
     * differs from the secret and bytes it did not make are refused.
     */
    public static class InMemorySecondFactorCipher implements SecondFactorCipher {

        private static final byte MARKER = 42;

        @Override
        public EncryptedSecret encrypt(TotpSecret secret) {
            byte[] plain = secret.base32().getBytes(StandardCharsets.US_ASCII);
            byte[] sealed = new byte[plain.length + 1];
            sealed[0] = MARKER;
            for (int i = 0; i < plain.length; i++) {
                sealed[plain.length - i] = plain[i];
            }
            return new EncryptedSecret(sealed);
        }

        @Override
        public Optional<TotpSecret> decrypt(EncryptedSecret secret) {
            byte[] sealed = secret.bytes();
            if (sealed[0] != MARKER) {
                return Optional.empty();
            }
            byte[] plain = new byte[sealed.length - 1];
            for (int i = 0; i < plain.length; i++) {
                plain[i] = sealed[plain.length - i];
            }
            try {
                return Optional.of(new TotpSecret(new String(plain, StandardCharsets.US_ASCII)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /** Fake of {@link RecoveryCodeGenerator}: {@code AAAAA00000}, {@code AAAAA00001}, ... continuing each call. */
    public static class InMemoryRecoveryCodeGenerator implements RecoveryCodeGenerator {

        private int next;

        @Override
        public synchronized List<SecondFactorCode.Recovery> newCodes() {
            return IntStream.range(0, COUNT)
                .mapToObj(i -> new SecondFactorCode.Recovery(String.format("AAAAA%05d", next++)))
                .toList();
        }
    }

    /** Fake of {@link RecoveryCodes}. */
    public static class InMemoryRecoveryCodes implements RecoveryCodes {

        private final Map<UserId, List<Code>> codes = new HashMap<>();

        private static final class Code {
            private final byte[] hash;
            private Instant usedAt;

            Code(byte[] hash) {
                this.hash = hash;
            }
        }

        @Override
        public synchronized void replace(UserId user, List<RecoveryCodeHash> hashes) {
            codes.put(user, new ArrayList<>(hashes.stream().map(hash -> new Code(hash.bytes())).toList()));
        }

        @Override
        public synchronized boolean consume(UserId user, RecoveryCodeHash hash, Instant now) {
            Optional<Code> unused = codes.getOrDefault(user, List.of()).stream()
                .filter(code -> code.usedAt == null && Arrays.equals(code.hash, hash.bytes()))
                .findFirst();
            unused.ifPresent(code -> code.usedAt = now);
            return unused.isPresent();
        }

        @Override
        public synchronized int unused(UserId user) {
            return (int) codes.getOrDefault(user, List.of()).stream().filter(code -> code.usedAt == null).count();
        }

        @Override
        public synchronized void deleteAll(UserId user) {
            codes.remove(user);
        }
    }

    /** Fake of {@link EnrollmentQrCode}: a data URI that carries the URI as text. */
    public static class InMemoryEnrollmentQrCode implements EnrollmentQrCode {

        @Override
        public String dataUri(String otpauthUri) {
            return "data:image/png;base64,QR(" + otpauthUri + ")";
        }
    }
}
