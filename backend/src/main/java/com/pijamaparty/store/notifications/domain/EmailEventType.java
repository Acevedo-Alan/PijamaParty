package com.pijamaparty.store.notifications.domain;

/** UC-17 (T-E3-22): what the email provider notifies about a sent email. */
public enum EmailEventType {

    /** The receiving server refused it for good. */
    PERMANENT_BOUNCE,

    /** The receiving server refused it for now, or did not say why: it may still arrive. */
    TRANSIENT_BOUNCE,

    /** The provider did not send it: the address bounced or complained before. */
    SUPPRESSED,

    /** It arrived, and the recipient marked it as spam. */
    COMPLAINT;

    /** Whether the email never reached its recipient, so it is {@link EmailStatus#BOUNCED} (decided in T-E3-22). */
    public boolean bouncesTheEmail() {
        return this == PERMANENT_BOUNCE || this == SUPPRESSED;
    }
}
