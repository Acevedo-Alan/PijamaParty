package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.VerifiedEmails;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-22 (T-E4-08): a customer's verified email for other modules, read in the caller's transaction. */
@Service
class VerifiedEmailsService implements VerifiedEmails {

    private final LoadUser users;

    VerifiedEmailsService(LoadUser users) {
        this.users = users;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> of(UserId customer) {
        return users.findById(customer)
            .filter(user -> user.role() == UserRole.CUSTOMER && user.emailVerified())
            .map(user -> user.email().value());
    }
}
