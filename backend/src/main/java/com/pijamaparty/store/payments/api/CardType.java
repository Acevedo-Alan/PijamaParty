package com.pijamaparty.store.payments.api;

/** UC-14 (T-E3-34): whether the card is a credit or a debit card, as MercadoPago's card form tells; its Orders API needs it. */
public enum CardType {
    CREDIT_CARD,
    DEBIT_CARD
}
