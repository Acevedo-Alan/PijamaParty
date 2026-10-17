package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Fake of {@link IssueUserTokens}, {@link ConsumeUserTokens} and {@link InvalidateUserTokens}. */
public class InMemoryUserTokens implements IssueUserTokens, ConsumeUserTokens, InvalidateUserTokens {

    private final InMemoryStore<UUID, UserToken> tokens = InMemoryStore.copying(UserToken::id, InMemoryUserTokens::copy);
    private final AtomicInteger lookups = new AtomicInteger();

    @Override
    public void add(UserToken token) {
        if (!tokens.findAll(stored -> stored.tokenHash().equals(token.tokenHash())).isEmpty()) {
            throw new IllegalStateException("token hashes are unique");
        }
        tokens.save(token);
    }

    /** Synchronized: the real adapter locks the token, so two openings of one link never interleave. */
    @Override
    public synchronized <R> R consuming(AccountToken token, Function<Optional<UserToken>, R> consume) {
        lookups.incrementAndGet();
        return consume.apply(tokens.findAll(stored -> stored.tokenHash().equals(token.hash())).stream().findFirst());
    }

    @Override
    public void markUsed(UserToken token) {
        UserToken stored = tokens.findById(token.id()).orElseThrow();
        if (stored.usedAt().isPresent() || token.usedAt().isEmpty()) {
            throw new IllegalStateException("the token was not unused");
        }
        tokens.save(token);
    }

    @Override
    public void invalidateUnused(UserId user, UserTokenPurpose purpose, Instant now) {
        for (UserToken token : tokens.findAll(stored -> stored.userId().equals(user))) {
            if (token.use(purpose, now)) {
                tokens.save(token);
            }
        }
    }

    public List<UserToken> all() {
        return tokens.findAll();
    }

    /** How many times a token was looked up. */
    public int lookups() {
        return lookups.get();
    }

    private static UserToken copy(UserToken token) {
        return UserToken.restore(token.id(), token.userId(), token.purpose(), token.tokenHash(),
            token.externalAccount().orElse(null), token.createdAt(), token.expiresAt(), token.usedAt().orElse(null));
    }
}
