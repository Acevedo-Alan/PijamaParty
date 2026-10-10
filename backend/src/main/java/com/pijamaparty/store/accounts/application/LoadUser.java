package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/** Outbound port: reads users within the caller's transaction and security context. */
public interface LoadUser {

    Optional<User> findById(UserId id);

    boolean administratorExists();
}
