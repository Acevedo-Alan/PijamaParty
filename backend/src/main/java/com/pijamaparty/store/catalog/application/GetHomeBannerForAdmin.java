package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-32: the administrator opens "Portada" and sees the banner as stored, published or not. */
public interface GetHomeBannerForAdmin {

    Result execute(Query query);

    record Query(Actor actor) {}

    sealed interface Result {

        record Found(HomeBannerView banner) implements Result {}

        record Forbidden() implements Result {}
    }
}
