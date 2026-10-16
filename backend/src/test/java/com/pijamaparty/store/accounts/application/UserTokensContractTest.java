package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Contract of {@link IssueUserTokens} and {@link ConsumeUserTokens} (UC-19). Implementations provide the user the
 * tokens belong to and how a token is issued (the real adapter joins the transaction of the email being sent).
 */
public abstract class UserTokensContractTest<T extends IssueUserTokens & ConsumeUserTokens> extends ContractTest<T> {

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
        return UserToken.issue(UUID.randomUUID(), customer.id(), purpose, token.hash(), NOW);
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
}
