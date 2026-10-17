package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: the password is checked and hashed first, holding no database connection (RNF-21), and
 * only then {@link CustomerAccountChanges} stores it in its own transaction, as the customer itself (V29 lets it set
 * its own password, never remove it). The account row is locked first, as in every change of the sign-in methods.
 */
@Service
class CreatePasswordService implements CreatePassword {

    private final CustomerAccountChanges changes;
    private final LoadSignInMethods methods;
    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final PasswordHasher passwords;
    private final Clock clock;

    CreatePasswordService(CustomerAccountChanges changes, LoadSignInMethods methods, LoadUser loadUser,
                          SaveUser saveUser, PasswordHasher passwords, Clock clock) {
        this.changes = changes;
        this.methods = methods;
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        if (!command.actor().is(Role.CUSTOMER) || command.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        Optional<PasswordRules.Violation> violation = PasswordRules.checkCustomer(command.password());
        if (violation.isPresent()) {
            return new Result.InvalidPassword(violation.get());
        }
        PasswordHash newPassword = passwords.hash(command.password());
        UserId customer = command.actor().userId().get();
        return changes.changing(() -> create(customer, newPassword));
    }

    private Result create(UserId customer, PasswordHash newPassword) {
        if (methods.lockByCustomer(customer).isEmpty()) {
            return new Result.NotSignedIn();
        }
        Optional<User> user = loadUser.findById(customer).filter(found -> found.role() == UserRole.CUSTOMER);
        if (user.isEmpty()) {
            return new Result.NotSignedIn();
        }
        if (!user.get().createPassword(newPassword, clock.instant())) {
            return new Result.AlreadySet();
        }
        saveUser.save(user.get());
        return new Result.Created();
    }
}
