package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;

/**
 * Outbound write port: makes the older links of a user stop working, in the caller's transaction, which consumes one
 * of its tokens as {@code SYSTEM} ({@link ConsumeUserTokens}).
 */
public interface InvalidateUserTokens {

    /** Marks used, at {@code now}, every token of {@code user} and {@code purpose} that is unused and not expired. */
    void invalidateUnused(UserId user, UserTokenPurpose purpose, Instant now);
}
