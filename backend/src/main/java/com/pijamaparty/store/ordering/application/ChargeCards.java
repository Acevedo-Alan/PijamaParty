package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import java.util.UUID;

/**
 * UC-14 (T-E3-29): charges an order's card at its provider, which also records the payment's result on the order.
 * Called outside any transaction, as the order's buyer: it waits for the provider.
 */
public interface ChargeCards {

    /** Charges exactly the order's total, with the order's email as the payer's. */
    PayOrderWithCard.Result charge(OrderToPay order, PayOrderWithCard.Card card, UUID idempotencyKey);
}
