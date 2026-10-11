package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.RecoveryCodeHash;
import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract of {@link RecoveryCodes}: each code works once, for its user only. */
public abstract class RecoveryCodesContractTest<T extends RecoveryCodes> extends ContractTest<T> {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final RecoveryCodeHash FIRST = new SecondFactorCode.Recovery("AAAAA00001").hash();
    private static final RecoveryCodeHash SECOND = new SecondFactorCode.Recovery("AAAAA00002").hash();

    private UserId owner;
    private UserId other;

    /** Stores the administrator the codes belong to; returns its id. */
    protected abstract UserId givenAdministrator();

    @BeforeEach
    void users() {
        owner = givenAdministrator();
        other = UserId.of(UUID.randomUUID());
    }

    @Test
    void givenCodes_whenOneIsUsed_thenItWorksOnceAndOnlyForItsUser() {
        subject.replace(owner, List.of(FIRST, SECOND));
        persistAndForget();

        assertThat(subject.consume(other, FIRST, NOW)).isFalse();
        assertThat(subject.consume(owner, FIRST, NOW)).isTrue();
        assertThat(subject.consume(owner, FIRST, NOW)).isFalse();
        assertThat(subject.unused(owner)).isOne();
    }

    @Test
    void givenCodes_whenReplaced_thenTheOldOnesNoLongerWork() {
        subject.replace(owner, List.of(FIRST));
        subject.replace(owner, List.of(SECOND));
        persistAndForget();

        assertThat(subject.consume(owner, FIRST, NOW)).isFalse();
        assertThat(subject.consume(owner, SECOND, NOW)).isTrue();
    }

    @Test
    void givenCodes_whenAllAreDeleted_thenNoneWorks() {
        subject.replace(owner, List.of(FIRST, SECOND));
        subject.deleteAll(owner);
        persistAndForget();

        assertThat(subject.unused(owner)).isZero();
        assertThat(subject.consume(owner, SECOND, NOW)).isFalse();
    }
}
