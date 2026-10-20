package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.ordering.api.WithdrawalNotices;
import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link WithdrawalNotices}: the requests added with {@link #requested}. */
class InMemoryWithdrawalNotices implements WithdrawalNotices {

    private final Map<UUID, WithdrawalNotice> notices = new ConcurrentHashMap<>();

    /** A request of order #1001 by María José Pérez ({@code ana@example.com}), code {@code ARR-7K2M9QXZ}. */
    WithdrawalNotice requested(boolean outOfTerm) {
        var notice = new WithdrawalNotice(UUID.randomUUID(), "ARR-7K2M9QXZ", 1001, "María José",
            "Pérez", "ana@example.com", outOfTerm);
        notices.put(notice.requestId(), notice);
        return notice;
    }

    @Override
    public Optional<WithdrawalNotice> find(UUID requestId) {
        return Optional.ofNullable(notices.get(requestId));
    }
}
