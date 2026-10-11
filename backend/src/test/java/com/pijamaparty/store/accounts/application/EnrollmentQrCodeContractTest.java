package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link EnrollmentQrCode}: a PNG data URI that carries the given URI. */
public abstract class EnrollmentQrCodeContractTest<T extends EnrollmentQrCode> extends ContractTest<T> {

    protected static final String URI =
        "otpauth://totp/Pijama%20Party:owner%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Pijama%20Party";

    /** What a scanner reads from the data URI. */
    protected abstract String scan(String dataUri);

    @Test
    void givenAnOtpauthUri_whenDrawn_thenItIsAPngDataUriThatScansBackToIt() {
        String dataUri = subject.dataUri(URI);

        assertThat(dataUri).startsWith("data:image/png;base64,");
        assertThat(scan(dataUri)).isEqualTo(URI);
    }
}
