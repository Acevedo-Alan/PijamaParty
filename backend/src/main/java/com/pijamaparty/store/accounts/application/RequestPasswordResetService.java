package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.UserRole;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link PasswordRecoveries} runs the lookup in its own transaction as {@code SYSTEM},
 * and the reset email is enqueued in it. A malformed email, an unknown one and the administrator's get the same
 * answer as a customer's, and nothing is sent.
 */
@Service
class RequestPasswordResetService implements RequestPasswordReset {

    private final PasswordRecoveries recoveries;
    private final AccountEvents events;

    RequestPasswordResetService(PasswordRecoveries recoveries, AccountEvents events) {
        this.recoveries = recoveries;
        this.events = events;
    }

    @Override
    public Result execute(Command command) {
        Optional<Email> email = Email.parse(command.email());
        email.ifPresent(found -> recoveries.requesting(found, user -> user
            .filter(customer -> customer.role() == UserRole.CUSTOMER)
            .ifPresent(customer -> events.passwordResetRequested(customer.id()))));
        return new Result.Requested();
    }
}
