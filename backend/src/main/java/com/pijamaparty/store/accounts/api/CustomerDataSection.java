package com.pijamaparty.store.accounts.api;

/**
 * UC-28 (T-E4-16): a section of the customer's data download that another module holds. Every section is always in
 * the file, as an empty list when no module contributes it.
 */
public enum CustomerDataSection {

    /** The customer's orders ({@code ordering}). */
    ORDERS,

    /** The customer's reviews ({@code reviews}, E5). */
    REVIEWS
}
