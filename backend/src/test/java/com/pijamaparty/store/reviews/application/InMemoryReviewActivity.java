package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link ReviewActivity}: keeps the entries in the order they were recorded. */
public class InMemoryReviewActivity extends InMemoryActivityEntries implements ReviewActivity {
}
