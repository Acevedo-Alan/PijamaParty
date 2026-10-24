package com.pijamaparty.store.activity.application;

import com.pijamaparty.store.activity.domain.ActivityPeriod;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListActivityService implements ListActivity {

    private final ActivityPages pages;
    private final Clock clock;

    ListActivityService(ActivityPages pages, Clock clock) {
        this.pages = pages;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<ActivityPeriod> period = ActivityPeriod.of(query.from(), query.to());
        if (period.isEmpty()) {
            return new Result.InvalidPeriod();
        }
        List<RecordedActivity> rows = pages.page(query.area(), period.get().start(clock.getZone()),
            period.get().end(clock.getZone()), Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
