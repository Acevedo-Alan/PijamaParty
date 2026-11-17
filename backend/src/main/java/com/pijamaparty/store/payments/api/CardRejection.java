package com.pijamaparty.store.payments.api;

/**
 * UC-14 (T-E3-29, T-E3-34): why a card was rejected, in the store's words; the storefront says each one plainly. The
 * reasons MercadoPago's Orders API tells apart (T-E3-34): it reports a mistyped security code or expiry date as card
 * data, and has no duplicate-payment reason.
 */
public enum CardRejection {
    INSUFFICIENT_FUNDS,
    /** A card detail was mistyped, or the card token was refused (expired, already used). */
    BAD_CARD_DATA,
    /** The bank asks the buyer to authorize the payment with it. */
    CALL_FOR_AUTHORIZE,
    CARD_DISABLED,
    MAX_ATTEMPTS,
    HIGH_RISK,
    OTHER
}
