package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;

/**
 * UC-14: binds an order's tracking token to the buyer of a request, so Row Level Security lets it create (the
 * checkout) or read (the tracking link) that order. Staff cannot carry one and are left as they are.
 */
final class TrackingActors {

    private TrackingActors() {
    }

    static Actor withTrackingToken(Actor actor, TrackingToken trackingToken) {
        return switch (actor.role()) {
            case ANONYMOUS -> Actor.anonymous(actor.cartToken(), Optional.of(trackingToken));
            case CUSTOMER -> Actor.customer(actor.userId().orElseThrow(), actor.cartToken(),
                Optional.of(trackingToken));
            case ADMIN, SYSTEM -> actor;
        };
    }
}
