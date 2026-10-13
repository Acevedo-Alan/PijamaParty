package com.pijamaparty.store.notifications.domain;

/** The emails the store sends (UC-17). Each one at most once per reference (RNF-20.7). */
public enum EmailType {

    /** To the buyer, when its order is paid: the only email of a purchase in v1 (RNF-23). The reference is the order. */
    ORDER_PAID_CUSTOMER,

    /** To the administrator, for every paid order. The reference is the order. */
    ORDER_PAID_ADMIN
}
