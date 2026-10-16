package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.UserToken;

/** Outbound write port: stores a new token of an email link in the caller's transaction, as {@code SYSTEM} (V29). */
public interface IssueUserTokens {

    void add(UserToken token);
}
