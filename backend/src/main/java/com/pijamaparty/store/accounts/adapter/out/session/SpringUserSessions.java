package com.pijamaparty.store.accounts.adapter.out.session;

import com.pijamaparty.store.accounts.application.EndUserSessions;
import com.pijamaparty.store.platform.domain.UserId;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Ends the sessions of Spring Session JDBC whose principal name is the user's id, as the sign-in writes it
 * ({@code SessionAuthentication}, RNF-27).
 */
@Component
class SpringUserSessions implements EndUserSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    SpringUserSessions(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void endAll(UserId user) {
        sessions.findByPrincipalName(user.toString()).keySet().forEach(sessions::deleteById);
    }
}
