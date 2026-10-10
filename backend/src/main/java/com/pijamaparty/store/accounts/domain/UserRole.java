package com.pijamaparty.store.accounts.domain;

/** The role stored with a user (column {@code app_user.role}): it separates the back office from customer accounts. */
public enum UserRole {
    CUSTOMER,
    ADMIN
}
