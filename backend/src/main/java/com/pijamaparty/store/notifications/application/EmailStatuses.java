package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Outbound read port (UC-18): where an email of the outbox stands, within the caller's transaction. */
public interface EmailStatuses {

    /** Of the latest version (T-E3-23); empty when the outbox has no email of this type and reference. */
    Optional<EmailStatus> statusOf(EmailType type, UUID reference);

    /** UC-33: the references whose latest email of this type is in one of {@code statuses}. */
    Set<UUID> referencesWithLatestIn(EmailType type, Set<EmailStatus> statuses);
}
