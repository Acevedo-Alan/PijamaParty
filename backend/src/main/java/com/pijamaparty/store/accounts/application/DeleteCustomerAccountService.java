package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: a password is checked first, holding no database connection (RNF-21), and only then
 * {@link CustomerAccountChanges} deletes the account in its own transaction, as the customer itself (V36). The account
 * row is locked first, as in every change of the sign-in methods, and a password replaced in between is refused. The
 * listeners of {@code CustomerAccountDeleted} join that transaction once the row is gone. A wrong password does not
 * count toward the lock of UC-19, so a stolen session cannot lock the owner out; the endpoint is rate-limited instead.
 */
@Service
class DeleteCustomerAccountService implements DeleteCustomerAccount {

    private final CustomerAccountChanges changes;
    private final LoadSignInMethods methods;
    private final LoadUser loadUser;
    private final DeleteCustomers customers;
    private final AccountEvents events;
    private final PasswordHasher passwords;
    private final EndUserSessions sessions;

    DeleteCustomerAccountService(CustomerAccountChanges changes, LoadSignInMethods methods, LoadUser loadUser,
                                 DeleteCustomers customers, AccountEvents events, PasswordHasher passwords,
                                 EndUserSessions sessions) {
        this.changes = changes;
        this.methods = methods;
        this.loadUser = loadUser;
        this.customers = customers;
        this.events = events;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    @Override
    public Result execute(Command command) {
        if (!command.actor().is(Role.CUSTOMER) || command.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        UserId customer = command.actor().userId().get();
        Optional<PasswordHash> confirmedPassword = Optional.empty();
        switch (command.confirmation()) {
            case Confirmation.None none -> {
                return new Result.ConfirmationRequired();
            }
            case Confirmation.Reauthenticated reauthenticated -> { }
            case Confirmation.Password typed -> {
                Optional<User> user = changes.changing(() -> customer(customer));
                if (user.isEmpty()) {
                    return new Result.NotSignedIn();
                }
                Optional<PasswordHash> hash = user.get().passwordHash();
                if (hash.isEmpty() || !passwords.matches(typed.password(), hash.get())) {
                    return new Result.WrongPassword();
                }
                confirmedPassword = hash;
            }
        }
        Optional<PasswordHash> expected = confirmedPassword;
        Result result = changes.changing(() -> delete(customer, expected));
        if (result instanceof Result.Deleted) {
            sessions.endAll(customer);
        }
        return result;
    }

    private Result delete(UserId customer, Optional<PasswordHash> expectedPassword) {
        if (methods.lockByCustomer(customer).isEmpty()) {
            return new Result.NotSignedIn();
        }
        Optional<User> user = customer(customer);
        if (user.isEmpty()) {
            return new Result.NotSignedIn();
        }
        if (expectedPassword.isPresent() && !user.get().passwordHash().equals(expectedPassword)) {
            return new Result.WrongPassword();
        }
        if (!customers.delete(customer)) {
            return new Result.NotSignedIn();
        }
        events.customerAccountDeleted(customer);
        return new Result.Deleted();
    }

    private Optional<User> customer(UserId customer) {
        return loadUser.findById(customer).filter(user -> user.role() == UserRole.CUSTOMER);
    }
}
