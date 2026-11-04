package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link SettingsActivity}: keeps the entries in the order they were recorded. */
public class InMemorySettingsActivity extends InMemoryActivityEntries implements SettingsActivity {
}
