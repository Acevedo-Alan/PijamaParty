package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-19: the single-use tokens of the email links. */
class UserTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00.123456789Z");
    private static final String HASH = "a".repeat(64);
    private static final UserId USER = UserId.of(UUID.randomUUID());

    @Test
    void givenEachPurpose_whenIssued_thenItLastsItsLifetimeFromNowToTheMicrosecond() {
        UserToken verification = UserToken.issue(UUID.randomUUID(), USER, UserTokenPurpose.EMAIL_VERIFICATION, HASH,
            NOW);
        UserToken reset = UserToken.issue(UUID.randomUUID(), USER, UserTokenPurpose.PASSWORD_RESET, HASH, NOW);

        assertThat(verification.createdAt()).isEqualTo(Instant.parse("2026-10-16T15:00:00.123456Z"));
        assertThat(verification.expiresAt()).isEqualTo(verification.createdAt().plus(Duration.ofHours(24)));
        assertThat(reset.expiresAt()).isEqualTo(reset.createdAt().plus(Duration.ofMinutes(30)));
        assertThat(verification.usedAt()).isEmpty();
    }

    @Test
    void givenAnUnusedTokenBeforeItsExpiry_whenUsedForItsPurpose_thenItIsUsedOnce() {
        UserToken token = UserToken.issue(UUID.randomUUID(), USER, UserTokenPurpose.PASSWORD_RESET, HASH, NOW);
        Instant at = NOW.plus(Duration.ofMinutes(29));

        assertThat(token.use(UserTokenPurpose.PASSWORD_RESET, at)).isTrue();
        assertThat(token.usedAt()).contains(Instant.parse("2026-10-16T15:29:00.123456Z"));
        assertThat(token.use(UserTokenPurpose.PASSWORD_RESET, at.plusSeconds(1))).isFalse();
    }

    @Test
    void givenAnExpiredTokenOrAnotherPurpose_whenUsed_thenNothingChanges() {
        UserToken token = UserToken.issue(UUID.randomUUID(), USER, UserTokenPurpose.EMAIL_VERIFICATION, HASH, NOW);

        assertThat(token.use(UserTokenPurpose.EMAIL_VERIFICATION, token.expiresAt())).isFalse();
        assertThat(token.use(UserTokenPurpose.PASSWORD_RESET, NOW)).isFalse();
        assertThat(token.usedAt()).isEmpty();
    }

    @Test
    void givenStoredValuesThatBreakTheRules_whenRestored_thenTheyAreRejected() {
        Instant created = Instant.parse("2026-10-16T15:00:00Z");
        Instant expires = created.plus(Duration.ofHours(24));

        assertThatThrownBy(() -> UserToken.restore(UUID.randomUUID(), USER, UserTokenPurpose.EMAIL_VERIFICATION,
            "not-a-hash", created, expires, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserToken.restore(UUID.randomUUID(), USER, UserTokenPurpose.PASSWORD_RESET, HASH,
            created, expires, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserToken.restore(UUID.randomUUID(), USER, UserTokenPurpose.EMAIL_VERIFICATION,
            HASH, created, expires, expires)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenALinkConfirmation_whenIssued_thenItLasts24HoursAndNamesTheExternalAccountItLinks() {
        var facebook = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");

        UserToken token = UserToken.issueLinkConfirmation(UUID.randomUUID(), USER, facebook, HASH, NOW);

        assertThat(token.purpose()).isEqualTo(UserTokenPurpose.LINK_CONFIRMATION);
        assertThat(token.expiresAt()).isEqualTo(token.createdAt().plus(Duration.ofHours(24)));
        assertThat(token.externalAccount()).contains(facebook);
        assertThat(token.use(UserTokenPurpose.LINK_CONFIRMATION, NOW.plus(Duration.ofHours(23)))).isTrue();
    }

    @Test
    void givenAnExternalAccountOnlyWhereALinkConfirmationIsNot_whenRestored_thenItIsRejected() {
        Instant created = Instant.parse("2026-10-16T15:00:00Z");
        Instant expires = created.plus(Duration.ofHours(24));
        var facebook = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");

        assertThatThrownBy(() -> UserToken.restore(UUID.randomUUID(), USER, UserTokenPurpose.LINK_CONFIRMATION, HASH,
            null, created, expires, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserToken.restore(UUID.randomUUID(), USER, UserTokenPurpose.EMAIL_VERIFICATION, HASH,
            facebook, created, expires, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserToken.issue(UUID.randomUUID(), USER, UserTokenPurpose.LINK_CONFIRMATION, HASH,
            NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
