package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * As every change of the customer's own account: {@link CustomerAccountChanges} runs it in its own short transaction,
 * as the customer itself (V29 lets it update its own row, as the checkout does with the phone), and the account row is
 * locked first, so a purchase that leaves its phone at the same time (UC-21) waits instead of losing either change.
 */
@Service
class ChangePhoneService implements ChangePhone {

    private final CustomerAccountChanges changes;
    private final LoadSignInMethods methods;
    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final Clock clock;

    ChangePhoneService(CustomerAccountChanges changes, LoadSignInMethods methods, LoadUser loadUser,
                       SaveUser saveUser, Clock clock) {
        this.changes = changes;
        this.methods = methods;
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        if (!command.actor().is(Role.CUSTOMER) || command.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        UserId customer = command.actor().userId().get();
        return changes.changing(() -> change(customer, command.phone()));
    }

    private Result change(UserId customer, String typed) {
        if (methods.lockByCustomer(customer).isEmpty()) {
            return new Result.NotSignedIn();
        }
        Optional<User> user = loadUser.findById(customer).filter(found -> found.role() == UserRole.CUSTOMER);
        if (user.isEmpty()) {
            return new Result.NotSignedIn();
        }
        return switch (user.get().changePhone(typed, clock.instant())) {
            case User.PhoneOutcome.Changed changed -> {
                saveUser.save(user.get());
                yield new Result.Changed(changed.phone());
            }
            case User.PhoneOutcome.Invalid invalid -> new Result.InvalidPhone();
        };
    }
}
