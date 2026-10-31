package com.pijamaparty.store.backoffice.application;

/** Outbound read port (UC-33): how many reviews wait for moderation (UC-25). */
public interface PendingReviewCount {

    long pending();
}
