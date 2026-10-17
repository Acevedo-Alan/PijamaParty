package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.LinkConfirmationLinks;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-27 (T-E4-14): mints the link confirmation token of an email about to be sent, naming the external account that
 * waits for the customer. Only its hash is kept (RNF-29.6); the token goes back to the caller, which puts it in the
 * link. Every link sent stays valid for its 24 hours.
 */
@Service
class IssueLinkConfirmationLinkService implements LinkConfirmationLinks {

    private final LoadUser users;
    private final LoadPendingLinks pendingLinks;
    private final IssueUserTokens userTokens;
    private final TokenGenerator tokens;
    private final IdGenerator ids;
    private final Clock clock;

    IssueLinkConfirmationLinkService(LoadUser users, LoadPendingLinks pendingLinks, IssueUserTokens userTokens,
                                     TokenGenerator tokens, IdGenerator ids, Clock clock) {
        this.users = users;
        this.pendingLinks = pendingLinks;
        this.userTokens = userTokens;
        this.tokens = tokens;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<LinkConfirmationLink> issue(UUID userId) {
        Optional<User> customer = users.findById(UserId.of(userId))
            .filter(user -> user.role() == UserRole.CUSTOMER);
        Optional<PendingLink> pending = customer.flatMap(user -> pendingLinks.find(user.id()));
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        var token = new AccountToken(tokens.newToken());
        userTokens.add(UserToken.issueLinkConfirmation(ids.newId(), customer.get().id(), pending.get().account(),
            token.hash(), clock.instant()));
        return Optional.of(new LinkConfirmationLink(customer.get().email().value(), customer.get().name(),
            Provider.valueOf(pending.get().account().provider().name()), token));
    }
}
