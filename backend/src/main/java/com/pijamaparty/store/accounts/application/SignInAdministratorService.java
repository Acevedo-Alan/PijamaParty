package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link UserSignInStore} runs short transactions as {@code SYSTEM}, so a failed attempt
 * is committed (the counter must survive the wrong password), and the slow password check runs between them,
 * holding no database connection.
 */
@Service
class SignInAdministratorService implements SignInAdministrator {

    private final UserSignInStore users;
    private final PasswordHasher passwords;
    private final Clock clock;

    SignInAdministratorService(UserSignInStore users, PasswordHasher passwords, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        String password = command.password() == null ? "" : command.password();
        Optional<Email> email = Email.parse(command.email());
        Optional<User> administrator = email.flatMap(users::find).filter(user -> user.role() == UserRole.ADMIN);
        if (administrator.isEmpty()) {
            passwords.matches(password, passwords.decoy());
            return new Result.InvalidCredentials();
        }
        if (administrator.get().isLockedAt(clock.instant())) {
            return new Result.Locked(administrator.get().lockedUntil().orElseThrow());
        }
        boolean matches = passwords.matches(password, administrator.get().passwordHash().orElseThrow());
        return users.withUserLockedForSignIn(email.get(), current -> record(current, matches));
    }

    /** Under the row lock: another attempt may have locked the account while the password was being checked. */
    private Result record(Optional<User> current, boolean passwordMatches) {
        Instant now = clock.instant();
        Optional<User> administrator = current.filter(user -> user.role() == UserRole.ADMIN);
        if (administrator.isEmpty()) {
            return new Result.InvalidCredentials();
        }
        User user = administrator.get();
        if (user.isLockedAt(now)) {
            return new Result.Locked(user.lockedUntil().orElseThrow());
        }
        if (passwordMatches) {
            user.registerSuccessfulSignIn(now);
            return new Result.SignedIn(AdministratorView.of(user));
        }
        return user.registerFailedSignIn(now)
            .<Result>map(Result.Locked::new)
            .orElseGet(Result.InvalidCredentials::new);
    }
}
