package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import java.util.Optional;

/** Outbound port: the user of an email, read within the caller's transaction and security context. */
public interface FindUserByEmail {

    Optional<User> findByEmail(Email email);
}
