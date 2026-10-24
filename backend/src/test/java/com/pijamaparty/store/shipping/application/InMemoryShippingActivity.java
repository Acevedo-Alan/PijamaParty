package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link ShippingActivity}: keeps the entries in the order they were recorded. */
public class InMemoryShippingActivity extends InMemoryActivityEntries implements ShippingActivity {
}
