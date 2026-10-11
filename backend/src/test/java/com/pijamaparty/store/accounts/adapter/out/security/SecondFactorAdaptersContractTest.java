package com.pijamaparty.store.accounts.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.pijamaparty.store.accounts.application.EnrollmentQrCodeContractTest;
import com.pijamaparty.store.accounts.application.SecondFactorCipherContractTest;
import com.pijamaparty.store.accounts.application.SecondFactorGeneratorsContractTest;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The real second-factor adapters against their contracts; plain JUnit, no Spring. */
class SecondFactorAdaptersContractTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Nested
    class Cipher extends SecondFactorCipherContractTest<AesGcmSecondFactorCipher> {

        @Override
        protected AesGcmSecondFactorCipher createSubject() {
            return new AesGcmSecondFactorCipher(KEY);
        }

        @Test
        void givenBytesEncryptedWithAnotherKey_whenDecrypted_thenNothingComesBack() {
            byte[] other = new byte[32];
            other[0] = 1;
            var sealed = new AesGcmSecondFactorCipher(Base64.getEncoder().encodeToString(other))
                .encrypt(new TotpSecret("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"));

            assertThat(subject.decrypt(sealed)).isEmpty();
        }

        @Test
        void givenAMissingOrShortKey_whenStarting_thenTheApiRefusesToStart() {
            assertThatIllegalStateException().isThrownBy(() -> new AesGcmSecondFactorCipher(""));
            assertThatIllegalStateException().isThrownBy(() -> new AesGcmSecondFactorCipher("not base64!"));
            assertThatIllegalStateException().isThrownBy(
                () -> new AesGcmSecondFactorCipher(Base64.getEncoder().encodeToString(new byte[16])));
        }
    }

    @Nested
    class Secrets extends SecondFactorGeneratorsContractTest.Secrets<RandomTotpSecretGenerator> {

        @Override
        protected RandomTotpSecretGenerator createSubject() {
            return new RandomTotpSecretGenerator();
        }
    }

    @Nested
    class Codes extends SecondFactorGeneratorsContractTest.RecoveryCodes<RandomRecoveryCodeGenerator> {

        @Override
        protected RandomRecoveryCodeGenerator createSubject() {
            return new RandomRecoveryCodeGenerator();
        }
    }

    @Nested
    class Qr extends EnrollmentQrCodeContractTest<ZxingEnrollmentQrCode> {

        @Override
        protected ZxingEnrollmentQrCode createSubject() {
            return new ZxingEnrollmentQrCode();
        }

        @Override
        protected String scan(String dataUri) {
            byte[] png = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
            try {
                var image = ImageIO.read(new ByteArrayInputStream(png));
                return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(image)))).getText();
            } catch (IOException | NotFoundException | ChecksumException | FormatException e) {
                throw new AssertionError("not a readable QR code", e);
            }
        }
    }
}
