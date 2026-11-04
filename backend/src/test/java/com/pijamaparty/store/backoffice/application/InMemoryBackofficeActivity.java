package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link BackofficeActivity}: keeps the entries in the order they were recorded. */
public class InMemoryBackofficeActivity extends InMemoryActivityEntries implements BackofficeActivity {
}
