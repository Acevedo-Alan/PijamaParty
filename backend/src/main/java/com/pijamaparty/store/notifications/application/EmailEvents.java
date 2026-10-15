package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import java.time.Instant;

/**
 * Outbound write port (UC-17, T-E3-22, RNF-20.3): the record of the email provider's notifications, once per event id.
 * Joins the caller's transaction, which must exist, so a notification whose processing fails is not recorded.
 */
public interface EmailEvents {

    /**
     * Records the notification unless the provider's event id is already recorded. A concurrent caller recording the
     * same one waits until the first transaction ends.
     *
     * @return true when recorded now, so the notification is to be processed; false when it already was
     */
    boolean recordFirst(MailProvider provider, String eventId, EmailEventType type, String providerMessageId,
                        Instant receivedAt);
}
