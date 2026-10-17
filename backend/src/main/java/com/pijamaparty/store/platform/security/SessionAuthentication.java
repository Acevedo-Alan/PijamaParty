package com.pijamaparty.store.platform.security;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Opens and closes the session of a user whose credentials a use case already checked (RNF-29.1). Signing in
 * changes the session id (no session fixation) and the CSRF token. The session stores only the user id and the
 * role: the principal name is the id, never the email (RNF-27, {@code spring_session.principal_name}). Every
 * {@link SignInListener} runs first, so a failing one leaves the visitor as it was.
 */
public final class SessionAuthentication {

    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();
    private final CsrfLogoutHandler forgetCsrfToken;
    private final List<SignInListener> listeners;

    SessionAuthentication(SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,
                          List<SignInListener> listeners) {
        this.contexts = contexts;
        var newCsrfToken = new CsrfAuthenticationStrategy(csrfTokens);
        // The SPA handler writes the new token right away, so the sign-in response already carries it.
        newCsrfToken.setRequestHandler(new SpaCsrfTokenRequestHandler());
        this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(), newCsrfToken));
        this.logout.setSecurityContextRepository(contexts);
        this.forgetCsrfToken = new CsrfLogoutHandler(csrfTokens);
        this.listeners = List.copyOf(listeners);
    }

    public void signIn(UserId userId, Role role, HttpServletRequest request, HttpServletResponse response) {
        if (role != Role.ADMIN && role != Role.CUSTOMER) {
            throw new IllegalArgumentException("only users sign in, not " + role);
        }
        for (SignInListener listener : listeners) {
            listener.signingIn(userId, role, request, response);
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(userId.toString(), null,
            List.of(new SimpleGrantedAuthority(SessionActorResolver.AUTHORITY_PREFIX + role.name())));
        sessionStrategy.onAuthentication(authentication, request, response);
        var context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    /** Invalidates the session, forgets the authentication and drops the CSRF token. */
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        var authentication = holder.getContext().getAuthentication();
        logout.logout(request, response, authentication);
        // The next person on a shared device does not inherit the CSRF token.
        forgetCsrfToken.logout(request, response, authentication);
    }
}
