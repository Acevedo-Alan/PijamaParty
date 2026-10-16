package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link CustomerRegistrations} runs the insert in its own short transaction as
 * {@code SYSTEM}, and the slow password hash runs before it, holding no database connection (RNF-21).
 */
@Service
class RegisterCustomerService implements RegisterCustomer {

    private final CustomerRegistrations registrations;
    private final PasswordHasher passwords;
    private final AccountEvents events;
    private final IdGenerator ids;
    private final Clock clock;

    RegisterCustomerService(CustomerRegistrations registrations, PasswordHasher passwords, AccountEvents events,
                            IdGenerator ids, Clock clock) {
        this.registrations = registrations;
        this.passwords = passwords;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        Optional<Email> email = Email.parse(command.email());
        if (email.isEmpty()) {
            return new Result.InvalidEmail();
        }
        String name = command.name() == null ? null : command.name().strip();
        if (!User.isValidName(name)) {
            return new Result.InvalidName();
        }
        Optional<PasswordRules.Violation> violation = PasswordRules.checkCustomer(command.password());
        if (violation.isPresent()) {
            return new Result.InvalidPassword(violation.get());
        }
        User customer = User.newCustomer(UserId.of(ids.newId()), email.get(), name,
            passwords.hash(command.password()), clock.instant());
        if (!registrations.register(customer, () -> events.emailVerificationRequested(customer.id()))) {
            return new Result.EmailAlreadyRegistered();
        }
        return new Result.Registered(customer.id());
    }
}
