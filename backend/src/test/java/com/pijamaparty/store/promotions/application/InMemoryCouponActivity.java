package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link CouponActivity}: keeps the entries in the order they were recorded. */
public class InMemoryCouponActivity extends InMemoryActivityEntries implements CouponActivity {
}
