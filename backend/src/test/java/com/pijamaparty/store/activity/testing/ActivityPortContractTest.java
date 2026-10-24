package com.pijamaparty.store.activity.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract shared by every module's activity port ({@code <Module>Activity}, UC-37): every entry recorded is read back
 * whole. Each module's contract test extends it with two entries of its own area.
 */
public abstract class ActivityPortContractTest<T> extends ContractTest<T> {

    /** Records through the port under test. */
    protected abstract void record(ActivityEntry entry);

    /** The entries recorded so far. */
    protected abstract List<ActivityEntry> recorded();

    /** An entry of the module's area with changed fields. */
    protected abstract ActivityEntry entryWithChanges();

    /** Another entry of the module's area, without changed fields. */
    protected abstract ActivityEntry entryWithoutChanges();

    @Test
    void givenAnEntryWithChanges_whenRecorded_thenItIsReadBackWhole() {
        ActivityEntry entry = entryWithChanges();

        record(entry);

        assertThat(recorded()).containsExactly(entry);
    }

    @Test
    void givenTwoEntries_whenRecorded_thenBothAreKept() {
        ActivityEntry first = entryWithChanges();
        ActivityEntry second = entryWithoutChanges();

        record(first);
        record(second);

        assertThat(recorded()).containsExactlyInAnyOrder(first, second);
    }
}
