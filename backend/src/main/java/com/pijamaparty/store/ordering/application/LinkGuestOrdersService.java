package com.pijamaparty.store.ordering.application;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs as {@code SYSTEM}, before the customer's session opens. The database checks again that each order goes to the
 * verified customer of its email and was never unlinked (V33).
 */
@Service
class LinkGuestOrdersService implements LinkGuestOrders {

    private final VerifiedCustomerEmails verifiedEmails;
    private final GuestOrderLinks links;
    private final Clock clock;

    LinkGuestOrdersService(VerifiedCustomerEmails verifiedEmails, GuestOrderLinks links, Clock clock) {
        this.verifiedEmails = verifiedEmails;
        this.links = links;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        Optional<String> email = verifiedEmails.of(command.customer());
        if (email.isEmpty()) {
            return new Result.NotVerified();
        }
        return new Result.Linked(links.link(command.customer(), email.get(), clock.instant()));
    }
}
