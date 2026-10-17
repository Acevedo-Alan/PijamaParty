package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link ConsumeUserTokens} runs the whole consumption in its own transaction, as
 * {@code SYSTEM} bound to the token's hash. The token is used first, so the database lets the email be verified and
 * the password of an unverified account be dropped (V34). It does not sign in (decided, T-E4-14).
 */
@Service
class ConfirmExternalLinkService implements ConfirmExternalLink {

    private final ConsumeUserTokens tokens;
    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final FindExternalIdentities identities;
    private final LinkExternalIdentities links;
    private final SavePendingLinks pendingLinks;
    private final IdGenerator ids;
    private final Clock clock;

    ConfirmExternalLinkService(ConsumeUserTokens tokens, LoadUser loadUser, SaveUser saveUser,
                               FindExternalIdentities identities, LinkExternalIdentities links,
                               SavePendingLinks pendingLinks, IdGenerator ids, Clock clock) {
        this.tokens = tokens;
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.identities = identities;
        this.links = links;
        this.pendingLinks = pendingLinks;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        // A token that could never have been issued is not looked up.
        if (!LinkTokens.isWellFormed(command.token())) {
            return new Result.InvalidLink();
        }
        return tokens.consuming(new AccountToken(command.token()), this::confirm);
    }

    private Result confirm(Optional<UserToken> found) {
        Instant now = clock.instant();
        if (found.isEmpty() || !found.get().use(UserTokenPurpose.LINK_CONFIRMATION, now)) {
            return new Result.InvalidLink();
        }
        tokens.markUsed(found.get());
        User customer = loadUser.findById(found.get().userId())
            .filter(user -> user.role() == UserRole.CUSTOMER)
            .orElseThrow(() -> new IllegalStateException("a link confirmation token belongs to a customer"));
        ExternalAccount account = found.get().externalAccount().orElseThrow();
        Optional<ExternalIdentity> linked = identities.find(account);
        if (linked.isPresent()) {
            return linked.get().userId().equals(customer.id()) ? new Result.Linked() : new Result.AlreadyLinked();
        }
        if (!links.link(ExternalIdentity.link(ids.newId(), customer.id(), account, customer.email(), now))) {
            return new Result.AlreadyLinked();
        }
        customer.verifyEmailThroughExternalAccount(now);
        saveUser.save(customer);
        pendingLinks.forget(customer.id());
        return new Result.Linked();
    }
}
