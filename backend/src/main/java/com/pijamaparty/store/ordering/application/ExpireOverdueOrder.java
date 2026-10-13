package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.Objects;

/**
 * UC-16: the oldest order still awaiting payment after its reservation expired becomes {@code EXPIRED} and releases
 * its stock, in one transaction. Runs as {@code SYSTEM}, the role Row Level Security lets update every order (RNF-27);
 * the job calls it until {@link Result.NoneDue}.
 */
public interface ExpireOverdueOrder {

    Result execute();

    sealed interface Result {

        record Expired(OrderId orderId) implements Result {

            public Expired {
                Objects.requireNonNull(orderId, "orderId");
            }
        }

        /** No order awaiting payment has an expired reservation, or another run is expiring it. */
        record NoneDue() implements Result {}
    }
}
