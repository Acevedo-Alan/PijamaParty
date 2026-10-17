package com.pijamaparty.store.platform.security;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Something a module does when a user signs in, whatever the way (password or, from T-E4-14, an external provider):
 * {@link SessionAuthentication} calls every listener after a use case checked the credentials and before it opens the
 * session, so a listener that fails leaves no session open. The request still acts as the visitor it was
 * ({@code CurrentActor}), and the listener can write to the response. E4 adds the cart's (UC-19, T-E4-04).
 */
@FunctionalInterface
public interface SignInListener {

    /** {@code role} is {@code ADMIN} or {@code CUSTOMER}. */
    void signingIn(UserId userId, Role role, HttpServletRequest request, HttpServletResponse response);
}
