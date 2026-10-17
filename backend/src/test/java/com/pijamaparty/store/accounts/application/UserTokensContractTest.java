package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link IssueUserTokens}, {@link ConsumeUserTokens} and {@link InvalidateUserTokens} (UC-19). Implementations provide the user the
 * tokens belong to and how a token is issued (the real adapter joins the transaction of the email being sent).
 */
public abstract class UserTokensContractTest<T extends IssueUserTokens & ConsumeUserTokens & InvalidateUserTokens>
    extends ContractTest<T> {

    protected static final Instant NOW = UserStoreContractTest.NOW;

    protected User customer;

    protected abstract void arrange(User user);

    protected void issue(UserToken token) {
        subject.add(token);
    }

    @BeforeEach
    void aCustomer() {
        customer = CustomerRegistrationsContractTest.customer("ana@example.com");
        arrange(customer);
    }

    protected UserToken token(AccountToken token, UserTokenPurpose purpose) {
        return token(token, purpose, NOW);
    }

    protected UserToken token(AccountToken token, UserTokenPurpose purpose, Instant createdAt) {
        return UserToken.issue(UUID.randomUUID(), customer.id(), purpose, token.hash(), createdAt);
    }

    @Test
    void givenAnIssuedToken_whenConsumingItsToken_thenItIsFoundAsIssued() {
        var link = new AccountToken("a".repeat(43));
        UserToken issued = token(link, UserTokenPurpose.EMAIL_VERIFICATION);
        issue(issued);

        Optional<UserToken> found = subject.consuming(link, token -> token);

        assertThat(found).get().usingRecursiveComparison().isEqualTo(issued);
        assertThat(found.orElseThrow().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void givenAnIssuedLinkConfirmation_whenConsumingItsToken_thenItNamesTheExternalAccountItLinks() {
        var link = new AccountToken("a".repeat(43));
        var facebook = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");
        UserToken issued = UserToken.issueLinkConfirmation(UUID.randomUUID(), customer.id(), facebook, link.hash(),
            NOW);
        issue(issued);

        Optional<UserToken> found = subject.consuming(link, token -> token);

        assertThat(found).get().usingRecursiveComparison().isEqualTo(issued);
        assertThat(found.orElseThrow().externalAccount()).contains(facebook);
        assertThat(found.orElseThrow().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void givenAnotherToken_whenConsuming_thenNothingIsFound() {
        issue(token(new AccountToken("a".repeat(43)), UserTokenPurpose.EMAIL_VERIFICATION));

        Optional<UserToken> found = subject.consuming(new AccountToken("b".repeat(43)), token -> token);

        assertThat(found).isEmpty();
    }

    @Test
    void givenATokenUsedAndMarked_whenConsumingItAgain_thenItIsFoundUsed() {
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.PASSWORD_RESET));
        Instant usedAt = NOW.plus(Duration.ofMinutes(5));

        subject.consuming(link, token -> {
            assertThat(token.orElseThrow().use(UserTokenPurpose.PASSWORD_RESET, usedAt)).isTrue();
            subject.markUsed(token.orElseThrow());
            return null;
        });

        Optional<UserToken> found = subject.consuming(link, token -> token);
        assertThat(found.orElseThrow().usedAt()).contains(usedAt);
    }

    @Test
    void givenATokenAlreadyMarkedUsed_whenMarkingItAgain_thenItIsRefused() {
        var link = new AccountToken("a".repeat(43));
        issue(token(link, UserTokenPurpose.EMAIL_VERIFICATION));
        subject.consuming(link, token -> {
            token.orElseThrow().use(UserTokenPurpose.EMAIL_VERIFICATION, NOW);
            subject.markUsed(token.orElseThrow());
            return null;
        });

        assertThatThrownBy(() -> subject.consuming(link, token -> {
            UserToken stale = UserToken.restore(token.orElseThrow().id(), customer.id(),
                UserTokenPurpose.EMAIL_VERIFICATION, link.hash(), NOW, NOW.plus(Duration.ofHours(24)), NOW);
            subject.markUsed(stale);
            return null;
        })).hasMessageContaining("the token was not unused");
    }

    @Test
    void givenOlderLinks_whenInvalidatingTheUnusedResetsOfACustomer_thenOnlyItsLiveResetTokensAreUsed() {
        User other = CustomerRegistrationsContractTest.customer("beto@example.com");
        arrange(other);
        var live = new AccountToken("a".repeat(43));
        var expired = new AccountToken("b".repeat(43));
        var verification = new AccountToken("c".repeat(43));
        var othersReset = new AccountToken("d".repeat(43));
        issue(token(live, UserTokenPurpose.PASSWORD_RESET));
        issue(token(expired, UserTokenPurpose.PASSWORD_RESET, NOW.minus(Duration.ofMinutes(31))));
        issue(token(verification, UserTokenPurpose.EMAIL_VERIFICATION));
        issue(UserToken.issue(UUID.randomUUID(), other.id(), UserTokenPurpose.PASSWORD_RESET, othersReset.hash(), NOW));
        Instant now = NOW.plus(Duration.ofMinutes(1));

        subject.consuming(new AccountToken("z".repeat(43)), token -> {
            subject.invalidateUnused(customer.id(), UserTokenPurpose.PASSWORD_RESET, now);
            return null;
        });

        assertThat(subject.consuming(live, token -> token).orElseThrow().usedAt()).contains(now);
        assertThat(subject.consuming(expired, token -> token).orElseThrow().usedAt()).isEmpty();
        assertThat(subject.consuming(verification, token -> token).orElseThrow().usedAt()).isEmpty();
        assertThat(subject.consuming(othersReset, token -> token).orElseThrow().usedAt()).isEmpty();
    }
}
