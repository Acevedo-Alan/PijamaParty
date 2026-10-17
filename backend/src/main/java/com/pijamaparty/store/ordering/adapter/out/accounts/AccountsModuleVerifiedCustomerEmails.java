package com.pijamaparty.store.ordering.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.VerifiedEmails;
import com.pijamaparty.store.ordering.application.VerifiedCustomerEmails;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Reads a customer's verified email through the public API of the accounts module. */
@Component
class AccountsModuleVerifiedCustomerEmails implements VerifiedCustomerEmails {

    private final VerifiedEmails verifiedEmails;

    AccountsModuleVerifiedCustomerEmails(VerifiedEmails verifiedEmails) {
        this.verifiedEmails = verifiedEmails;
    }

    @Override
    public Optional<String> of(UserId customer) {
        return verifiedEmails.of(customer);
    }
}
