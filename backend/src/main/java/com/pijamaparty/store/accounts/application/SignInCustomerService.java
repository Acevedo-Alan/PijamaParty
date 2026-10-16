package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}, as the administrator's sign-in: {@link UserSignInStore} runs short transactions as
 * {@code SYSTEM}, so a failed attempt is committed, and the slow password check runs between them, holding no
 * database connection. The administrator never signs in here: its account gets the same answer as an unknown email.
 */
@Service
class SignInCustomerService implements SignInCustomer {

    private final UserSignInStore users;
    private final PasswordHasher passwords;
    private final AccountEvents events;
    private final Clock clock;

    SignInCustomerService(UserSignInStore users, PasswordHasher passwords, AccountEvents events, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        String password = command.password() == null ? "" : command.password();
        Optional<Email> email = Email.parse(command.email());
        Optional<User> customer = email.flatMap(users::find).filter(SignInCustomerService::signsInWithPassword);
        if (customer.isEmpty()) {
            passwords.matches(password, passwords.decoy());
            return new Result.InvalidCredentials();
        }
        if (customer.get().isLockedAt(clock.instant())) {
            return new Result.Locked(customer.get().lockedUntil().orElseThrow());
        }
        boolean matches = passwords.matches(password, customer.get().passwordHash().orElseThrow());
        return users.withUserLockedForSignIn(email.get(), current -> record(current, matches));
    }

    private static boolean signsInWithPassword(User user) {
        return user.role() == UserRole.CUSTOMER && user.passwordHash().isPresent();
    }

    /** Under the row lock: another attempt may have locked the account while the password was being checked. */
    private Result record(Optional<User> current, boolean passwordMatches) {
        Instant now = clock.instant();
        Optional<User> customer = current.filter(SignInCustomerService::signsInWithPassword);
        if (customer.isEmpty()) {
            return new Result.InvalidCredentials();
        }
        User user = customer.get();
        if (user.isLockedAt(now)) {
            return new Result.Locked(user.lockedUntil().orElseThrow());
        }
        if (!passwordMatches) {
            return user.registerFailedSignIn(now)
                .<Result>map(Result.Locked::new)
                .orElseGet(Result.InvalidCredentials::new);
        }
        if (!user.emailVerified()) {
            // Neither a failure nor a sign-in: the attempts stay as they are.
            events.emailVerificationRequested(user.id());
            return new Result.EmailNotVerified();
        }
        user.registerSuccessfulSignIn(now);
        return new Result.SignedIn(CustomerView.of(user));
    }
}
