package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link EndUserSessions}: records whose sessions were ended. */
public class InMemoryUserSessions implements EndUserSessions {

    private final List<UserId> ended = new CopyOnWriteArrayList<>();

    @Override
    public void endAll(UserId user) {
        ended.add(user);
    }

    public List<UserId> ended() {
        return List.copyOf(ended);
    }
}
