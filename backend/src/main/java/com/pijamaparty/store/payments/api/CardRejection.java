package com.pijamaparty.store.payments.api;

/** UC-14 (T-E3-29): why a card was rejected, in the store's words; the storefront says each one plainly. */
public enum CardRejection {
    INSUFFICIENT_FUNDS,
    BAD_SECURITY_CODE,
    BAD_EXPIRY_DATE,
    /** The card number or another field was mistyped, or the card token was refused (expired, already used). */
    BAD_CARD_DATA,
    /** The bank asks the buyer to authorize the payment with it. */
    CALL_FOR_AUTHORIZE,
    CARD_DISABLED,
    /** The same payment was just made. */
    DUPLICATED,
    MAX_ATTEMPTS,
    HIGH_RISK,
    OTHER
}
