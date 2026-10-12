package com.pijamaparty.store.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/** T-E3-28: the user key of the back-office rate-limit policy, read from the session before Spring Security runs. */
class SessionUserKeyResolverTest {

    private final SessionUserKeyResolver resolver = new SessionUserKeyResolver(new HttpSessionSecurityContextRepository());

    @Test
    void givenASignedInSession_whenResolved_thenTheKeyIsTheUserIdOfThatSession() {
        String admin = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();

        assertThat(resolve(signedIn(admin))).contains(admin);
        assertThat(resolve(signedIn(admin))).isEqualTo(resolve(signedIn(admin)));
        assertThat(resolve(signedIn(other))).contains(other);
    }

    @Test
    void givenNoSignedInSession_whenResolved_thenThereIsNoUserAndNoSessionIsCreated() {
        var withoutSession = new MockHttpServletRequest("GET", "/api/admin/shipping");
        assertThat(resolver.resolve(withoutSession)).isEmpty();
        assertThat(withoutSession.getSession(false)).isNull();

        var emptySession = new MockHttpServletRequest("GET", "/api/admin/shipping");
        emptySession.setSession(new MockHttpSession());
        assertThat(resolver.resolve(emptySession)).isEmpty();

        assertThat(resolve(new AnonymousAuthenticationToken("key", "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))))).isEmpty();
    }

    private static Authentication signedIn(String userId) {
        return UsernamePasswordAuthenticationToken.authenticated(userId, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Optional<String> resolve(Authentication authentication) {
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            new SecurityContextImpl(authentication));
        var request = new MockHttpServletRequest("GET", "/api/admin/shipping");
        request.setSession(session);
        return resolver.resolve(request);
    }
}
