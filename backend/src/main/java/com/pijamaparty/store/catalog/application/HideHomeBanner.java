package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-32: the administrator takes the banner off the home page. Hiding needs nothing and can be repeated. */
public interface HideHomeBanner {

    Result execute(Command command);

    record Command(Actor actor) {}

    sealed interface Result {

        record Hidden(HomeBannerView banner) implements Result {}

        record Forbidden() implements Result {}
    }
}
