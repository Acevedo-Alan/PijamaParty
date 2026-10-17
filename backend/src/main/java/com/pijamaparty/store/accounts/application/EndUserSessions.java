package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.UserId;

/** Outbound port: signs a user out everywhere, ending each of its sessions (UC-19, T-E4-03). */
public interface EndUserSessions {

    void endAll(UserId user);
}
