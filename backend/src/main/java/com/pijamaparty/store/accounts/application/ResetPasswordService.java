package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: the new password is checked and hashed first, holding no database connection (RNF-21),
 * and only then {@link ConsumeUserTokens} runs the consumption in its own transaction, as {@code SYSTEM} bound to the
 * token's hash. The token is used first, so the database lets the password be replaced and the email verified. Once
 * that commits, every session of the account ends.
 */
@Service
class ResetPasswordService implements ResetPassword {

    private final ConsumeUserTokens tokens;
    private final InvalidateUserTokens olderLinks;
    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final PasswordHasher passwords;
    private final EndUserSessions sessions;
    private final Clock clock;

    ResetPasswordService(ConsumeUserTokens tokens, InvalidateUserTokens olderLinks, LoadUser loadUser,
                         SaveUser saveUser, PasswordHasher passwords, EndUserSessions sessions, Clock clock) {
        this.tokens = tokens;
        this.olderLinks = olderLinks;
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.passwords = passwords;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        if (!LinkTokens.isWellFormed(command.token())) {
            return new Result.InvalidLink();
        }
        Optional<PasswordRules.Violation> violation = PasswordRules.checkCustomer(command.password());
        if (violation.isPresent()) {
            return new Result.InvalidPassword(violation.get());
        }
        PasswordHash newPassword = passwords.hash(command.password());
        Optional<UserId> reset = tokens.consuming(new AccountToken(command.token()),
            found -> reset(found, newPassword));
        if (reset.isEmpty()) {
            return new Result.InvalidLink();
        }
        sessions.endAll(reset.get());
        return new Result.Reset();
    }

    /** @return the customer whose password was replaced; empty when the link cannot be used */
    private Optional<UserId> reset(Optional<UserToken> found, PasswordHash newPassword) {
        Instant now = clock.instant();
        if (found.isEmpty() || !found.get().use(UserTokenPurpose.PASSWORD_RESET, now)) {
            return Optional.empty();
        }
        tokens.markUsed(found.get());
        olderLinks.invalidateUnused(found.get().userId(), UserTokenPurpose.PASSWORD_RESET, now);
        User customer = loadUser.findById(found.get().userId())
            .filter(user -> user.role() == UserRole.CUSTOMER)
            .orElseThrow(() -> new IllegalStateException("a password reset token belongs to a customer"));
        customer.resetPassword(newPassword, now);
        saveUser.save(customer);
        return Optional.of(customer.id());
    }
}
