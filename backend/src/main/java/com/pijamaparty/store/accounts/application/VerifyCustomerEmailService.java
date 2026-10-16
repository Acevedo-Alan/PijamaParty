package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link ConsumeUserTokens} runs the whole consumption in its own transaction, as
 * {@code SYSTEM} bound to the token's hash. The token is used first, so the database lets the email be verified. A
 * token of an account already verified (a second link) is used too, and changes nothing else.
 */
@Service
class VerifyCustomerEmailService implements VerifyCustomerEmail {

    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{" + TokenGenerator.LENGTH + "}");

    private final ConsumeUserTokens tokens;
    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final Clock clock;

    VerifyCustomerEmailService(ConsumeUserTokens tokens, LoadUser loadUser, SaveUser saveUser, Clock clock) {
        this.tokens = tokens;
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        // A token that could never have been issued is not looked up.
        if (command.token() == null || !WELL_FORMED.matcher(command.token()).matches()) {
            return new Result.InvalidLink();
        }
        return tokens.consuming(new AccountToken(command.token()), this::verify);
    }

    private Result verify(Optional<UserToken> found) {
        Instant now = clock.instant();
        if (found.isEmpty() || !found.get().use(UserTokenPurpose.EMAIL_VERIFICATION, now)) {
            return new Result.InvalidLink();
        }
        tokens.markUsed(found.get());
        User customer = loadUser.findById(found.get().userId())
            .filter(user -> user.role() == UserRole.CUSTOMER)
            .orElseThrow(() -> new IllegalStateException("a verification token belongs to a customer"));
        customer.verifyEmail(now);
        saveUser.save(customer);
        return new Result.Verified();
    }
}
