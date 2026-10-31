package com.pijamaparty.store.backoffice.application;

/** Fake of {@link OrderCounts}: how many paid orders wait, as the test sets it. */
public class InMemoryOrderCounts implements OrderCounts {

    private long paid;

    /** Test arrangement: one more order paid. */
    public synchronized void paid() {
        paid++;
    }

    @Override
    public synchronized long paidWaiting() {
        return paid;
    }
}
