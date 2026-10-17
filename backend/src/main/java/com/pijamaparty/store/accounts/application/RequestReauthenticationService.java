package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read as the customer itself: the database shows a customer only its own external accounts (V34). */
@Service
class RequestReauthenticationService implements RequestReauthentication {

    private final LoadSignInMethods methods;

    RequestReauthenticationService(LoadSignInMethods methods) {
        this.methods = methods;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Command command) {
        if (!command.actor().is(Role.CUSTOMER) || command.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        Optional<SignInMethods> found = methods.findByCustomer(command.actor().userId().get());
        if (found.isEmpty()) {
            return new Result.NotSignedIn();
        }
        return found.get().identity(command.provider()).isPresent()
            ? new Result.Requested()
            : new Result.NotLinked();
    }
}
