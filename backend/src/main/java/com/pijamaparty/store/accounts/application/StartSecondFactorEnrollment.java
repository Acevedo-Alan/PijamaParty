package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;

/**
 * UC-04, enrollment (T-E1-16): a new secret for the administrator's authenticator app, as text, as an
 * {@code otpauth://} URI and as its QR code. Nothing is stored: the caller keeps the encrypted secret in the pending
 * sign-in until {@link ConfirmSecondFactorEnrollment}.
 */
public interface StartSecondFactorEnrollment {

    /** Shown in the authenticator app next to the account. */
    String ISSUER = "Pijama Party";

    Result execute(Command command);

    record Command(UserId administrator) {

        public Command {
            Objects.requireNonNull(administrator, "administrator");
        }
    }

    sealed interface Result {

        /** {@code secretText} and {@code otpauthUri} hold the secret: shown to the administrator only, never logged. */
        record Started(EncryptedSecret secret, String secretText, String otpauthUri, String qrCode) implements Result {

            @Override
            public String toString() {
                return "Started[***]";
            }
        }

        /** It already has a second factor: the sign-in goes through the code instead. */
        record AlreadyEnrolled() implements Result {}

        /** The user is gone or is not the administrator. */
        record NotPending() implements Result {}
    }
}
