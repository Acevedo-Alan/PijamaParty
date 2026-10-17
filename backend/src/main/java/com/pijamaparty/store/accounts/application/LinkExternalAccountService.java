package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link ExternalSignIns} runs it in one transaction as {@code SYSTEM}, which the database
 * lets link external accounts to customers (V34), as the sign-in does. The identity keeps the provider's email, or the
 * account's when the provider gave none, as a confirmed link does. A Google account vouching for the account's own
 * email verifies it; it is linked first, so the database lets it (V34), and an unverified account loses its password
 * in that same update (T-E4-13).
 */
@Service
class LinkExternalAccountService implements LinkExternalAccount {

    private final ExternalSignIns signIns;
    private final FindExternalIdentities identities;
    private final LinkExternalIdentities links;
    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final IdGenerator ids;
    private final Clock clock;

    LinkExternalAccountService(ExternalSignIns signIns, FindExternalIdentities identities,
                               LinkExternalIdentities links, LoadUser loadUser, SaveUser saveUser, IdGenerator ids,
                               Clock clock) {
        this.signIns = signIns;
        this.identities = identities;
        this.links = links;
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        return signIns.signingIn(() -> link(command));
    }

    private Result link(Command command) {
        Optional<User> customer = loadUser.findById(command.customer())
            .filter(user -> user.role() == UserRole.CUSTOMER);
        if (customer.isEmpty()) {
            return new Result.NotFound();
        }
        User user = customer.get();
        ExternalAccount account = command.account();
        Optional<ExternalIdentity> linked = identities.find(account);
        if (linked.isPresent()) {
            return linked.get().userId().equals(user.id()) ? new Result.Linked() : new Result.AlreadyLinked();
        }
        if (identities.hasProvider(user.id(), account.provider())) {
            return new Result.AlreadyLinked();
        }
        Optional<Email> providerEmail = command.email().flatMap(Email::parse);
        Email email = providerEmail.orElse(user.email());
        Instant now = clock.instant();
        if (!links.link(ExternalIdentity.link(ids.newId(), user.id(), account, email, now))) {
            return new Result.AlreadyLinked();
        }
        boolean vouched = account.provider().vouchesForEmails() && command.emailVerified()
            && providerEmail.equals(Optional.of(user.email()));
        if (vouched && !user.emailVerified()) {
            user.verifyEmailThroughExternalAccount(now);
            saveUser.save(user);
        }
        return new Result.Linked();
    }
}
