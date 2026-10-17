package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link ExternalSignIns} runs it in one transaction as {@code SYSTEM}. An email typed by
 * the visitor proves nothing, so it never links an existing account (decided, security review of T-E4-14):
 * <ul>
 *   <li>without an account of that email, an unverified customer without a password and without external accounts
 *       is created and gets the link confirmation, so nobody can sign in to it until the link is confirmed;</li>
 *   <li>a customer waiting for the link confirmation of this same external account (the visitor asks again) gets it
 *       again;</li>
 *   <li>any other customer gets the link attempt notice, which links nothing.</li>
 * </ul>
 * The administrator's email, a customer that already has an account of that provider, and an external account already
 * linked get nothing. The answer is always the same.
 */
@Service
class RequestExternalLinkConfirmationService implements RequestExternalLinkConfirmation {

    private final ExternalSignIns signIns;
    private final FindExternalIdentities identities;
    private final FindUserByEmail usersByEmail;
    private final AddExternalCustomers customers;
    private final LoadPendingLinks loadPendingLinks;
    private final SavePendingLinks savePendingLinks;
    private final AccountEvents events;
    private final IdGenerator ids;
    private final Clock clock;

    RequestExternalLinkConfirmationService(ExternalSignIns signIns, FindExternalIdentities identities,
                                           FindUserByEmail usersByEmail, AddExternalCustomers customers,
                                           LoadPendingLinks loadPendingLinks, SavePendingLinks savePendingLinks,
                                           AccountEvents events, IdGenerator ids, Clock clock) {
        this.signIns = signIns;
        this.identities = identities;
        this.usersByEmail = usersByEmail;
        this.customers = customers;
        this.loadPendingLinks = loadPendingLinks;
        this.savePendingLinks = savePendingLinks;
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
        return signIns.signingIn(() -> request(command.account(), email.get(), command.name()));
    }

    private Result request(ExternalAccount account, Email email, Optional<String> name) {
        if (identities.find(account).isPresent()) {
            return new Result.Requested();
        }
        Instant now = clock.instant();
        Optional<User> existing = usersByEmail.findByEmail(email);
        if (existing.isEmpty()) {
            User created = User.newExternalCustomer(UserId.of(ids.newId()), email,
                User.nameFromProvider(name.orElse(null), email), false, now);
            // A registration took the email first: its owner gets that account's emails, not this one.
            if (customers.add(created)) {
                savePendingLinks.request(new PendingLink(created.id(), account, now));
                events.linkConfirmationRequested(created.id());
            }
            return new Result.Requested();
        }
        User customer = existing.get();
        if (customer.role() != UserRole.CUSTOMER || identities.hasProvider(customer.id(), account.provider())) {
            return new Result.Requested();
        }
        Optional<PendingLink> pending = loadPendingLinks.lockByCustomer(customer.id());
        if (pending.isPresent() && pending.get().account().equals(account)) {
            savePendingLinks.request(new PendingLink(customer.id(), account, now));
            events.linkConfirmationRequested(customer.id());
        } else {
            events.linkAttemptNoticeRequested(customer.id());
        }
        return new Result.Requested();
    }
}
