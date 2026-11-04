package com.pijamaparty.store.catalog.application;

/** Fake of {@link LowStockThreshold}: the units the test sets, 3 (the store's default) until then. */
public class InMemoryLowStockThreshold implements LowStockThreshold {

    private volatile int units = 3;

    @Override
    public int units() {
        return units;
    }

    /** Test arrangement: the threshold saved in "Datos de la tienda" (UC-36). */
    public void set(int units) {
        this.units = units;
    }
}
