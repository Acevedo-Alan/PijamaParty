package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.OrderAlert;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-18: the back-office order detail, composed here because it needs the ordering and the notifications modules
 * (ARCHITECTURE.md section 3, rule 3): the order with what it may move to, its active withdrawal request (UC-29) and
 * its alerts.
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

        /**
         * @param withdrawal the order's received withdrawal request, if any (T-E3-26)
         * @param alerts most urgent first
         */
        record Found(AdminOrder order, Optional<ActiveWithdrawal> withdrawal, List<OrderAlert> alerts)
            implements Result {

            public Found {
                Objects.requireNonNull(order, "order");
                Objects.requireNonNull(withdrawal, "withdrawal");
                alerts = List.copyOf(alerts);
            }
        }

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
