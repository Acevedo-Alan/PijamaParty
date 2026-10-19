package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Set;

/** UC-32: the administrator edits the banner's title, text, link text and link target, all together. */
public interface UpdateHomeBanner {

    Result execute(Command command);

    /** The texts as typed: the use case trims and checks them. */
    record Command(Actor actor, String title, String body, String linkText, String linkPath) {}

    sealed interface Result {

        record Updated(HomeBannerView banner) implements Result {}

        /** Nothing changed: these texts break a rule. */
        record Invalid(Set<HomeBanner.Field> fields) implements Result {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }

        record Forbidden() implements Result {}
    }
}
