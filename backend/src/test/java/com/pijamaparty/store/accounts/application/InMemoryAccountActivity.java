package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link AccountActivity}: keeps the entries in the order they were recorded. */
public class InMemoryAccountActivity extends InMemoryActivityEntries implements AccountActivity {
}
