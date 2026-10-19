package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Set;

/** UC-32: the administrator shows the banner on the home page; it needs its photo, title, link text and target. */
public interface PublishHomeBanner {

    Result execute(Command command);

    record Command(Actor actor) {}

    sealed interface Result {

        record Published(HomeBannerView banner) implements Result {}

        /** Nothing changed: the banner stays hidden without these. */
        record Incomplete(Set<HomeBanner.Part> missing) implements Result {

            public Incomplete {
                missing = Set.copyOf(missing);
            }
        }

        record Forbidden() implements Result {}
    }
}
