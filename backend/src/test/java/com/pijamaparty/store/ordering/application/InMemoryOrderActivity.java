package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link OrderActivity}: keeps the entries in the order they were recorded. */
public class InMemoryOrderActivity extends InMemoryActivityEntries implements OrderActivity {
}
