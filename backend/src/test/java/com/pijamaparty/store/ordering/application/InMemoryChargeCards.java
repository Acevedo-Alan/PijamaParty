package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link ChargeCards}: answers what the test chose (approved by default). Records the charged orders. */
public class InMemoryChargeCards implements ChargeCards {

    private final List<OrderToPay> charged = new CopyOnWriteArrayList<>();
    private volatile PayOrderWithCard.Result answer = new PayOrderWithCard.Result.Approved();

    @Override
    public PayOrderWithCard.Result charge(OrderToPay order, PayOrderWithCard.Card card, UUID idempotencyKey) {
        charged.add(order);
        return answer;
    }

    /** The next charges answer this. */
    public void answers(PayOrderWithCard.Result next) {
        answer = next;
    }

    /** Every order charged, in order. */
    public List<OrderToPay> charged() {
        return List.copyOf(charged);
    }
}
