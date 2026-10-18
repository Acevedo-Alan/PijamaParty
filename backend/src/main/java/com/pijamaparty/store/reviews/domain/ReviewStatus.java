package com.pijamaparty.store.reviews.domain;

/** UC-24, UC-25: a review shows on the product page only once the administrator approves it. */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
