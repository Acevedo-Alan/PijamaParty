package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.OrderAlert;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-18: the back-office order detail, composed here because it needs the ordering and the notifications modules
 * (ARCHITECTURE.md section 3, rule 3): the order with what it may move to, and its alerts.
 */
public interface GetOrderDetail {

    Result execute(Query query);

    record Query(Actor actor, UUID orderId) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(orderId, "orderId");
        }
    }

    sealed interface Result {

        /** @param alerts most urgent first */
        record Found(AdminOrder order, List<OrderAlert> alerts) implements Result {

            public Found {
                Objects.requireNonNull(order, "order");
                alerts = List.copyOf(alerts);
            }
        }

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
