package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.PasswordResetLinks;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
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
 * UC-19 (T-E4-03): mints the reset token of an email about to be sent. Only its hash is kept (RNF-29.6); the token
 * goes back to the caller, which puts it in the link. Any customer gets one, verified or not, with a password or not.
 */
@Service
class IssuePasswordResetLinkService implements PasswordResetLinks {

    private final LoadUser users;
    private final IssueUserTokens userTokens;
    private final TokenGenerator tokens;
    private final IdGenerator ids;
    private final Clock clock;

    IssuePasswordResetLinkService(LoadUser users, IssueUserTokens userTokens, TokenGenerator tokens, IdGenerator ids,
                                  Clock clock) {
        this.users = users;
        this.userTokens = userTokens;
        this.tokens = tokens;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<PasswordResetLink> issue(UUID userId) {
        Optional<User> customer = users.findById(UserId.of(userId)).filter(user -> user.role() == UserRole.CUSTOMER);
        if (customer.isEmpty()) {
            return Optional.empty();
        }
        var token = new AccountToken(tokens.newToken());
        userTokens.add(UserToken.issue(ids.newId(), customer.get().id(), UserTokenPurpose.PASSWORD_RESET,
            token.hash(), clock.instant()));
        return Optional.of(new PasswordResetLink(customer.get().email().value(), customer.get().name(), token));
    }
}
