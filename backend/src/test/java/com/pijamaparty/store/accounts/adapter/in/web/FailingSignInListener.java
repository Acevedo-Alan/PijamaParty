package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.security.SignInListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A sign-in listener that does nothing until a test arms it, and then fails the next sign-in with the given error,
 * as a module's listener failing on the database would. Tests {@link #reset()} it after.
 */
public class FailingSignInListener implements SignInListener {

    private final AtomicReference<RuntimeException> next = new AtomicReference<>();

    public void failNextWith(RuntimeException error) {
        next.set(error);
    }

    public void reset() {
        next.set(null);
    }

    @Override
    public void signingIn(UserId userId, Role role, HttpServletRequest request, HttpServletResponse response) {
        RuntimeException error = next.getAndSet(null);
        if (error != null) {
            throw error;
        }
    }
}
