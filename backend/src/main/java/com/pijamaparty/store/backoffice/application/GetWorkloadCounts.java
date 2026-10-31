package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * T-E8-05: the counts of the back-office menu, a small part of the summary (UC-33) so the menu does not read it all:
 * the number of {@code PAID} orders waiting, shown on "Pedidos".
 */
public interface GetWorkloadCounts {

    Result execute(Query query);

    record Query(Actor actor) {

        public Query {
            Objects.requireNonNull(actor, "actor");
        }
    }

    sealed interface Result {

        record Counted(long paidOrders) implements Result {}

        record Forbidden() implements Result {}
    }
}
