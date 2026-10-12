package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-12: the back office reads the cost of each province and the free-shipping threshold. */
public interface GetShippingConfiguration {

    Result execute(Query query);

    record Query(Actor actor) {}

    sealed interface Result {

        record Found(ShippingConfiguration configuration) implements Result {}

        record Forbidden() implements Result {}
    }
}
