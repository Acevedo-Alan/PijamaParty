package com.pijamaparty.store.activity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link DeleteOldActivity} (D-E8-11). The entries are around the real 2-year cutoff of today, because Row
 * Level Security lets the system delete only the entries older than that by the database's own clock (V41).
 */
public abstract class DeleteOldActivityContractTest<T extends DeleteOldActivity> extends ContractTest<T> {

    /** Arranges an entry as already stored, bypassing the port under test. */
    protected abstract void stored(RecordedActivity activity);

    /** The ids of the entries still stored. */
    protected abstract Set<UUID> remaining();

    @Test
    void givenEntriesOnBothSidesOfTheCutoff_whenTheOldOnesAreDeleted_thenOnlyThoseGo_andTheirNumberIsAnswered() {
        Instant now = Instant.now();
        Instant cutoff = now.atOffset(ZoneOffset.UTC).minusYears(2).toInstant();
        RecordedActivity threeYearsOld = entry(cutoff.minusSeconds(365L * 24 * 3600));
        RecordedActivity justPast = entry(cutoff.minusSeconds(60));
        RecordedActivity oneYearOld = entry(now.minusSeconds(365L * 24 * 3600));
        RecordedActivity today = entry(now.minusSeconds(60));
        List.of(threeYearsOld, justPast, oneYearOld, today).forEach(this::stored);

        int removed = subject.deleteOccurredBefore(cutoff);
        persistAndForget();

        assertThat(removed).isEqualTo(2);
        assertThat(remaining()).containsExactlyInAnyOrder(oneYearOld.id(), today.id());
    }

    @Test
    void givenNoOldEntries_whenTheOldOnesAreDeleted_thenNothingGoes() {
        RecordedActivity recent = entry(Instant.now().minusSeconds(60));
        stored(recent);

        assertThat(subject.deleteOccurredBefore(Instant.now().atOffset(ZoneOffset.UTC).minusYears(2).toInstant()))
            .isZero();
        assertThat(remaining()).containsExactly(recent.id());
    }

    private static RecordedActivity entry(Instant occurredAt) {
        return new RecordedActivity(UUID.randomUUID(), occurredAt, new ActivityEntry(RlsPolicyTester.ADMIN_ID,
            ActivityAction.STOCK_ADJUSTED, ActivitySubject.of(SubjectType.VARIANT, UUID.randomUUID(), "PP-AB12CD34")));
    }
}
