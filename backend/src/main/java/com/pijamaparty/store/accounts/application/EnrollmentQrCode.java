package com.pijamaparty.store.accounts.application;

/** Outbound port: the QR code the authenticator app scans to enroll (T-E1-16). */
public interface EnrollmentQrCode {

    /** A PNG of the QR code of {@code otpauthUri}, as a {@code data:image/png;base64,...} URI. */
    String dataUri(String otpauthUri);
}
