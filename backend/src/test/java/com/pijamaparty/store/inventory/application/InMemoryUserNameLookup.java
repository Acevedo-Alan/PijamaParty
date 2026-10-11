package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Fake of {@link UserNameLookup}: only the users added to it are visible. */
public class InMemoryUserNameLookup implements UserNameLookup {

    private final Map<UserId, String> names = new ConcurrentHashMap<>();

    public void add(UserId id, String name) {
        names.put(id, name);
    }

    @Override
    public Map<UserId, String> namesOf(Set<UserId> ids) {
        return ids.stream().filter(names::containsKey).collect(Collectors.toUnmodifiableMap(id -> id, names::get));
    }
}
