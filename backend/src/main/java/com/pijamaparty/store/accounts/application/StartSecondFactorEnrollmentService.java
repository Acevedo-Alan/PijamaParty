package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Reads the user through {@link UserSignInStore}, which runs as {@code SYSTEM}: nobody is signed in yet. */
@Service
class StartSecondFactorEnrollmentService implements StartSecondFactorEnrollment {

    private final UserSignInStore users;
    private final TotpSecretGenerator secrets;
    private final SecondFactorCipher cipher;
    private final EnrollmentQrCode qrCodes;

    StartSecondFactorEnrollmentService(UserSignInStore users, TotpSecretGenerator secrets, SecondFactorCipher cipher,
                                       EnrollmentQrCode qrCodes) {
        this.users = users;
        this.secrets = secrets;
        this.cipher = cipher;
        this.qrCodes = qrCodes;
    }

    @Override
    public Result execute(Command command) {
        Optional<User> administrator = users.find(command.administrator())
            .filter(user -> user.role() == UserRole.ADMIN);
        if (administrator.isEmpty()) {
            return new Result.NotPending();
        }
        if (administrator.get().secondFactor().isPresent()) {
            return new Result.AlreadyEnrolled();
        }
        TotpSecret secret = secrets.newSecret();
        String uri = otpauthUri(administrator.get().email().value(), secret);
        return new Result.Started(cipher.encrypt(secret), secret.base32(), uri, qrCodes.dataUri(uri));
    }

    /** The key URI format of authenticator apps: label "issuer:account", and the parameters of RFC 6238. */
    static String otpauthUri(String account, TotpSecret secret) {
        String label = encode(ISSUER) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret.base32() + "&issuer=" + encode(ISSUER)
            + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
