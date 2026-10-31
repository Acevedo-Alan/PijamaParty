package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link PendingReviewCount} (UC-33): only the reviews still pending wait for moderation. */
public abstract class PendingReviewCountContractTest<T extends PendingReviewCount> extends ContractTest<T> {

    /** A review in {@code status}: PENDING, APPROVED or REJECTED. */
    protected abstract void givenReview(String status);

    @Test
    void givenReviewsInEveryStatus_whenCounted_thenOnlyThePendingOnesWait() {
        givenReview("PENDING");
        givenReview("PENDING");
        givenReview("APPROVED");
        givenReview("REJECTED");
        persistAndForget();

        assertThat(subject.pending()).isEqualTo(2);
    }
}
