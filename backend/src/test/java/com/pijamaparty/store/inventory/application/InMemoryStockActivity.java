package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link StockActivity}: keeps the entries in the order they were recorded. */
public class InMemoryStockActivity extends InMemoryActivityEntries implements StockActivity {
}
