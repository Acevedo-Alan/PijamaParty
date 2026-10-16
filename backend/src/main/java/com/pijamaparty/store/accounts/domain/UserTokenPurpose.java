package com.pijamaparty.store.accounts.domain;

import java.time.Duration;

/** What the single-use token of an email link is for, and how long it lasts (UC-19, column {@code purpose}, V29). */
public enum UserTokenPurpose {

    EMAIL_VERIFICATION(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofMinutes(30));

    private final Duration lifetime;

    UserTokenPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public Duration lifetime() {
        return lifetime;
    }
}
