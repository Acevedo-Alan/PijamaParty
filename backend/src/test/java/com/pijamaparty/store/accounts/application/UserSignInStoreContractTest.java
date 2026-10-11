package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.SecondFactor;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract of {@link UserSignInStore}. Implementations provide how to arrange and read back a stored user. */
public abstract class UserSignInStoreContractTest<T extends UserSignInStore> extends ContractTest<T> {

    private static final Email OWNER = Email.parse("owner@example.com").orElseThrow();

    protected abstract void arrange(User user);

    protected abstract Optional<User> stored(UserId id);

    @Test
    void givenAStoredUser_whenAnAttemptChangesIt_thenTheChangeIsSavedAndTheResultReturned() {
        User user = UserStoreContractTest.administrator();
        arrange(user);
        Instant later = UserStoreContractTest.NOW.plusSeconds(60);

        String result = subject.withUserLockedForSignIn(OWNER, found -> {
            found.orElseThrow().registerFailedSignIn(later);
            return "attempt result";
        });

        assertThat(result).isEqualTo("attempt result");
        User after = stored(user.id()).orElseThrow();
        assertThat(after.failedSignInAttempts()).isOne();
        assertThat(after.updatedAt()).isEqualTo(later);
    }

    @Test
    void givenAnAttemptThatLocksTheAccount_whenSaved_thenTheLockIsStored() {
        User user = UserStoreContractTest.administrator();
        arrange(user);
        Instant later = UserStoreContractTest.NOW.plusSeconds(60);

        Optional<Instant> lockedUntil = subject.withUserLockedForSignIn(OWNER, found -> {
            User locking = found.orElseThrow();
            Optional<Instant> until = Optional.empty();
            for (int i = 0; i < User.MAX_CONSECUTIVE_FAILURES; i++) {
                until = locking.registerFailedSignIn(later);
            }
            return until;
        });

        assertThat(stored(user.id()).orElseThrow().lockedUntil()).isEqualTo(lockedUntil).isPresent();
    }

    @Test
    void givenAStoredUser_whenFindingItByEmail_thenItIsReturnedAndAnUnknownEmailFindsNothing() {
        User user = UserStoreContractTest.administrator();
        arrange(user);

        assertThat(subject.find(OWNER)).map(User::id).contains(user.id());
        assertThat(subject.find(Email.parse("nobody@example.com").orElseThrow())).isEmpty();
    }

    @Test
    void givenAStoredUser_whenAnAttemptByIdEnrollsASecondFactor_thenItIsSavedAndFoundById() {
        User user = UserStoreContractTest.administrator();
        arrange(user);
        Instant later = UserStoreContractTest.NOW.plusSeconds(60);

        subject.withUserLockedForSignIn(user.id(), found -> {
            found.orElseThrow().enrollSecondFactor(new EncryptedSecret(new byte[] {7}), 42L, later);
            return null;
        });

        assertThat(stored(user.id()).orElseThrow().secondFactor()).map(SecondFactor::lastStep).contains(42L);
        assertThat(subject.find(user.id())).map(User::id).contains(user.id());
        assertThat(subject.find(UserId.of(java.util.UUID.randomUUID()))).isEmpty();
        boolean unknownFound = subject.withUserLockedForSignIn(UserId.of(java.util.UUID.randomUUID()),
            Optional::isPresent);
        assertThat(unknownFound).isFalse();
    }

    @Test
    void givenAnUnknownEmail_whenAttempting_thenTheAttemptSeesNoUser() {
        arrange(UserStoreContractTest.administrator());

        boolean found = subject.withUserLockedForSignIn(Email.parse("nobody@example.com").orElseThrow(),
            Optional::isPresent);

        assertThat(found).isFalse();
    }
}
