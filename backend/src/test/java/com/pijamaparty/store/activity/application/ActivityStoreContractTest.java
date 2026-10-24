package com.pijamaparty.store.activity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityArea;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link SaveActivity} and {@link ActivityPages} (UC-37): what is saved reads back the same, newest first,
 * filtered by area and by a half-open period. Entries are the administrator's of {@link RlsPolicyTester}, so the real
 * adapter can run as it under Row Level Security.
 */
public abstract class ActivityStoreContractTest<T extends SaveActivity & ActivityPages> extends ContractTest<T> {

    protected static final Instant T0 = Instant.parse("2026-10-15T15:00:00Z");

    @Test
    void givenASavedEntry_whenItIsRead_thenEveryPartIsAsSaved() {
        RecordedActivity saved = new RecordedActivity(UUID.randomUUID(), T0, new ActivityEntry(
            RlsPolicyTester.ADMIN_ID, ActivityAction.VARIANT_UPDATED,
            ActivitySubject.of(SubjectType.VARIANT, UUID.randomUUID(), "PP-AB12CD34"),
            List.of(FieldChange.of("price", "$20.000", "$22.000"), FieldChange.of("active", null, "Sí"),
                FieldChange.of("note", "algo", null))));

        subject.save(saved);
        persistAndForget();

        assertThat(page(Optional.empty())).containsExactly(saved);
    }

    @Test
    void givenAnEntryWithoutSubjectIdLabelOrChanges_whenItIsRead_thenThoseStayEmpty() {
        RecordedActivity saved = entry(ActivityAction.SHIPPING_ZONES_CHANGED, T0);

        subject.save(saved);
        persistAndForget();

        RecordedActivity read = page(Optional.empty()).getFirst();
        assertThat(read.entry().subject()).isEqualTo(ActivitySubject.of(SubjectType.SHIPPING));
        assertThat(read.entry().changes()).isEmpty();
    }

    @Test
    void givenEntriesAtDifferentTimes_whenAPageIsRead_thenTheyComeNewestFirst_andTiesByIdDescending() {
        RecordedActivity oldest = entry(ActivityAction.PRODUCT_PUBLISHED, T0);
        RecordedActivity newest = entry(ActivityAction.PRODUCT_HIDDEN, T0.plusSeconds(120));
        RecordedActivity tieLow = new RecordedActivity(new UUID(0, 1), T0.plusSeconds(60),
            entry(ActivityAction.STOCK_ADJUSTED, T0).entry());
        RecordedActivity tieHigh = new RecordedActivity(new UUID(0, 2), T0.plusSeconds(60),
            entry(ActivityAction.STOCK_ADJUSTED, T0).entry());
        List.of(oldest, tieLow, newest, tieHigh).forEach(subject::save);
        persistAndForget();

        assertThat(page(Optional.empty())).extracting(RecordedActivity::id)
            .containsExactly(newest.id(), tieHigh.id(), tieLow.id(), oldest.id());
    }

    @Test
    void givenEntriesOfSeveralAreas_whenOneAreaIsAsked_thenOnlyItsEntriesCome() {
        RecordedActivity stock = entry(ActivityAction.STOCK_ADJUSTED, T0);
        RecordedActivity coupon = entry(ActivityAction.COUPON_CREATED, T0.plusSeconds(1));
        RecordedActivity anotherStock = entry(ActivityAction.STOCK_ADJUSTED, T0.plusSeconds(2));
        List.of(stock, coupon, anotherStock).forEach(subject::save);
        persistAndForget();

        assertThat(page(Optional.of(ActivityArea.STOCK))).extracting(RecordedActivity::id)
            .containsExactly(anotherStock.id(), stock.id());
        assertThat(page(Optional.of(ActivityArea.CONTENT))).isEmpty();
    }

    @Test
    void givenAPeriod_whenAPageIsRead_thenItsStartIsInside_andItsEndIsOutside() {
        Instant start = T0;
        Instant end = T0.plusSeconds(3600);
        RecordedActivity before = entry(ActivityAction.COUPON_CREATED, start.minusSeconds(1));
        RecordedActivity atStart = entry(ActivityAction.COUPON_CREATED, start);
        RecordedActivity lastInside = entry(ActivityAction.COUPON_CREATED, end.minusSeconds(1));
        RecordedActivity atEnd = entry(ActivityAction.COUPON_CREATED, end);
        List.of(before, atStart, lastInside, atEnd).forEach(subject::save);
        persistAndForget();

        assertThat(subject.page(Optional.empty(), Optional.of(start), Optional.of(end), 0, 10))
            .extracting(RecordedActivity::id).containsExactly(lastInside.id(), atStart.id());
        assertThat(subject.page(Optional.empty(), Optional.of(start), Optional.empty(), 0, 10))
            .extracting(RecordedActivity::id).containsExactly(atEnd.id(), lastInside.id(), atStart.id());
        assertThat(subject.page(Optional.empty(), Optional.empty(), Optional.of(start), 0, 10))
            .extracting(RecordedActivity::id).containsExactly(before.id());
    }

    @Test
    void givenMoreEntriesThanTheLimit_whenPagesAreRead_thenEachSkipsTheOffset() {
        List<RecordedActivity> entries = List.of(entry(ActivityAction.REVIEW_APPROVED, T0),
            entry(ActivityAction.REVIEW_APPROVED, T0.plusSeconds(1)),
            entry(ActivityAction.REVIEW_APPROVED, T0.plusSeconds(2)));
        entries.forEach(subject::save);
        persistAndForget();

        assertThat(subject.page(Optional.empty(), Optional.empty(), Optional.empty(), 0, 2))
            .extracting(RecordedActivity::id).containsExactly(entries.get(2).id(), entries.get(1).id());
        assertThat(subject.page(Optional.empty(), Optional.empty(), Optional.empty(), 2, 2))
            .extracting(RecordedActivity::id).containsExactly(entries.get(0).id());
        assertThat(subject.page(Optional.empty(), Optional.empty(), Optional.empty(), 3, 2)).isEmpty();
    }

    private List<RecordedActivity> page(Optional<ActivityArea> area) {
        return subject.page(area, Optional.empty(), Optional.empty(), 0, 50);
    }

    /** An entry of the administrator about the shipping table, without changes. */
    protected static RecordedActivity entry(ActivityAction action, Instant occurredAt) {
        return new RecordedActivity(UUID.randomUUID(), occurredAt,
            new ActivityEntry(RlsPolicyTester.ADMIN_ID, action, ActivitySubject.of(SubjectType.SHIPPING)));
    }
}
