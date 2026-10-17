package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
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
 * Not {@code @Transactional}: {@link ExternalSignIns} runs the whole decision in one transaction as {@code SYSTEM}, so
 * an account is never created without its external account, nor linked without being verified. The external account
 * is linked before the email is verified: the database lets Google verify only through a linked Google account with
 * that email (V34).
 */
@Service
class SignInWithExternalAccountService implements SignInWithExternalAccount {

    private final ExternalSignIns signIns;
    private final FindExternalIdentities identities;
    private final LinkExternalIdentities links;
    private final LoadUser loadUser;
    private final FindUserByEmail usersByEmail;
    private final SaveUser saveUser;
    private final AddExternalCustomers customers;
    private final LoadPendingLinks loadPendingLinks;
    private final SavePendingLinks savePendingLinks;
    private final AccountEvents events;
    private final IdGenerator ids;
    private final Clock clock;

    SignInWithExternalAccountService(ExternalSignIns signIns, FindExternalIdentities identities,
                                     LinkExternalIdentities links, LoadUser loadUser, FindUserByEmail usersByEmail,
                                     SaveUser saveUser, AddExternalCustomers customers,
                                     LoadPendingLinks loadPendingLinks, SavePendingLinks savePendingLinks,
                                     AccountEvents events, IdGenerator ids, Clock clock) {
        this.signIns = signIns;
        this.identities = identities;
        this.links = links;
        this.loadUser = loadUser;
        this.usersByEmail = usersByEmail;
        this.saveUser = saveUser;
        this.customers = customers;
        this.loadPendingLinks = loadPendingLinks;
        this.savePendingLinks = savePendingLinks;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        return signIns.signingIn(() -> signIn(command));
    }

    private Result signIn(Command command) {
        ExternalAccount account = command.account();
        Optional<ExternalIdentity> linked = identities.find(account);
        if (linked.isPresent()) {
            return loadUser.findById(linked.get().userId())
                .filter(user -> user.role() == UserRole.CUSTOMER)
                .<Result>map(customer -> new Result.SignedIn(customer.id()))
                .orElseGet(Result.Refused::new);
        }
        if (command.email().isEmpty()) {
            return new Result.EmailRequired();
        }
        Optional<Email> email = Email.parse(command.email().get());
        if (email.isEmpty()) {
            return new Result.Refused();
        }
        boolean vouched = account.provider().vouchesForEmails() && command.emailVerified();
        Instant now = clock.instant();
        Optional<User> existing = usersByEmail.findByEmail(email.get());
        if (existing.isEmpty()) {
            return create(account, email.get(), vouched, command.name(), now);
        }
        User user = existing.get();
        if (user.role() != UserRole.CUSTOMER) {
            return new Result.Refused();
        }
        if (identities.hasProvider(user.id(), account.provider())) {
            return new Result.ProviderAlreadyLinked();
        }
        if (!vouched) {
            // The first request wins (UC-27): a fresh link of another external account keeps waiting, unanswered.
            Optional<PendingLink> pending = loadPendingLinks.lockByCustomer(user.id());
            if (pending.isEmpty() || pending.get().yieldsTo(account, now)) {
                savePendingLinks.request(new PendingLink(user.id(), account, now));
                events.linkConfirmationRequested(user.id());
            }
            return new Result.LinkConfirmationSent();
        }
        if (!links.link(ExternalIdentity.link(ids.newId(), user.id(), account, user.email(), now))) {
            return new Result.Refused();
        }
        user.verifyEmailThroughExternalAccount(now);
        saveUser.save(user);
        return new Result.SignedIn(user.id());
    }

    private Result create(ExternalAccount account, Email email, boolean vouched, Optional<String> name,
                          Instant now) {
        User customer = User.newExternalCustomer(UserId.of(ids.newId()), email,
            User.nameFromProvider(name.orElse(null), email), vouched, now);
        if (!customers.add(customer)) {
            return new Result.Refused();
        }
        if (!links.link(ExternalIdentity.link(ids.newId(), customer.id(), account, email, now))) {
            // Another sign-in linked this external account first: this account must not stay without it.
            throw new ConcurrentExternalSignInException();
        }
        if (!vouched) {
            events.emailVerificationRequested(customer.id());
        }
        return new Result.SignedIn(customer.id());
    }
}
