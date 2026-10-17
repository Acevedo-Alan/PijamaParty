package com.pijamaparty.store.notifications.domain;

/** The emails the store sends (UC-17, UC-19, UC-29). Each one at most once per reference and version (RNF-20.7). */
public enum EmailType {

    /** To the buyer, when its order is paid: the only email of a purchase in v1 (RNF-23). The reference is the order. */
    ORDER_PAID_CUSTOMER,

    /** To the administrator, for every paid order. The reference is the order. */
    ORDER_PAID_ADMIN,

    /**
     * To the buyer, the procedure code of its withdrawal request: required by the regulation, an exception to the
     * single email of RNF-23 (UC-29). The reference is the request.
     */
    WITHDRAWAL_RECEIVED_CUSTOMER,

    /** To the administrator, for every withdrawal request (UC-29). The reference is the request. */
    WITHDRAWAL_RECEIVED_ADMIN,

    /**
     * To a customer who registered, the link to verify its email (UC-19, T-E4-02). The reference is the user; each new
     * link asked for is a new version.
     */
    EMAIL_VERIFICATION,

    /**
     * To a customer who forgot its password, the link to set a new one (UC-19, T-E4-03). The reference is the user;
     * each new link asked for is a new version.
     */
    PASSWORD_RESET,

    /**
     * To a customer whose email a provider does not vouch for (Facebook), the link that confirms linking that external
     * account (UC-27, T-E4-14). The reference is the user; each new request is a new version.
     */
    LINK_CONFIRMATION,

    /**
     * To a customer whose email someone typed to connect an external account without email, the notice that nothing
     * was linked and how to link it from "Mi cuenta" (UC-27, T-E4-14). No token. The reference is the user; each new
     * attempt is a new version.
     */
    LINK_ATTEMPT_NOTICE
}
