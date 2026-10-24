package com.pijamaparty.store.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityArea;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-37 and D-E8-11: recording, listing and purging the activity log, with in-memory fakes. */
class ActivityServicesTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);
    private static final UUID NEXT_ID = UUID.randomUUID();

    private final InMemoryActivityStore store = new InMemoryActivityStore();
    private final Clock clock = Clock.fixed(NOW, ARGENTINA);
    private final ActivityLogService log = new ActivityLogService(store, () -> NEXT_ID, clock);
    private final ListActivityService list = new ListActivityService(store, clock);
    private final PurgeOldActivityService purge = new PurgeOldActivityService(store, clock);

    // --- Recording ---

    @Test
    void givenAPriceChange_whenRecorded_thenTheEntryKeepsItWithTheTimeOfTheClockAndANewId() {
        UUID variant = UUID.randomUUID();
        ActivityEntry entry = new ActivityEntry(ADMIN_ID, ActivityAction.VARIANT_UPDATED,
            ActivitySubject.of(SubjectType.VARIANT, variant, "PP-AB12CD34"),
            List.of(FieldChange.of("price", "$20.000", "$22.000")));

        log.record(entry);

        assertThat(store.all()).containsExactly(new RecordedActivity(NEXT_ID, NOW, entry));
    }

    @Test
    void givenALongValueAndALongLabel_whenRecorded_thenBothAreCutTo200Characters() {
        log.record(new ActivityEntry(ADMIN_ID, ActivityAction.PRODUCT_UPDATED,
            ActivitySubject.of(SubjectType.PRODUCT, UUID.randomUUID(), "n".repeat(300)),
            List.of(FieldChange.of("description", "a".repeat(5000), "b"))));

        ActivityEntry stored = store.all().getFirst().entry();
        assertThat(stored.subject().label()).hasValueSatisfying(label -> assertThat(label).hasSize(200).endsWith("…"));
        assertThat(stored.changes().getFirst().before())
            .hasValueSatisfying(before -> assertThat(before).hasSize(200).endsWith("…"));
        assertThat(stored.changes().getFirst().after()).contains("b");
    }

    @Test
    void givenMoreThan20ChangedFields_whenRecorded_thenItIsRefusedAndNothingIsStored() {
        List<FieldChange> changes = IntStream.rangeClosed(1, 21)
            .mapToObj(i -> FieldChange.of("field" + i, "a", "b")).toList();

        assertThatThrownBy(() -> log.record(new ActivityEntry(ADMIN_ID, ActivityAction.STORE_SETTINGS_CHANGED,
            ActivitySubject.of(SubjectType.STORE_SETTINGS), changes))).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.all()).isEmpty();
    }

    @Test
    void givenAFieldThatIsNotAKey_whenRecorded_thenItIsRefused() {
        assertThatThrownBy(() -> log.record(new ActivityEntry(ADMIN_ID, ActivityAction.STORE_SETTINGS_CHANGED,
            ActivitySubject.of(SubjectType.STORE_SETTINGS), List.of(FieldChange.of("Correo electrónico", "a", "b")))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Listing ---

    @Test
    void givenACustomer_whenListing_thenItIsForbidden() {
        Actor customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(list.execute(query(customer, Optional.empty(), Optional.empty(), Optional.empty(), 0)))
            .isInstanceOf(ListActivity.Result.Forbidden.class);
    }

    @Test
    void givenFromAfterTo_whenListing_thenThePeriodIsInvalid() {
        assertThat(list.execute(query(ADMIN, Optional.empty(), Optional.of(LocalDate.of(2026, 10, 15)),
            Optional.of(LocalDate.of(2026, 10, 1)), 0))).isInstanceOf(ListActivity.Result.InvalidPeriod.class);
    }

    @Test
    void givenStockEntriesOnStoreDates_whenFilteredByStockAndAPeriod_thenOnlyThoseOfItsArgentinaDaysCome() {
        RecordedActivity lastMinuteOfSept30 = saved(ActivityAction.STOCK_ADJUSTED, "2026-10-01T02:59:00Z");
        RecordedActivity firstMinuteOfOct1 = saved(ActivityAction.STOCK_ADJUSTED, "2026-10-01T03:00:00Z");
        RecordedActivity lastMinuteOfOct15 = saved(ActivityAction.STOCK_ADJUSTED, "2026-10-16T02:59:00Z");
        RecordedActivity firstMinuteOfOct16 = saved(ActivityAction.STOCK_ADJUSTED, "2026-10-16T03:00:00Z");
        RecordedActivity couponOnOct10 = saved(ActivityAction.COUPON_CREATED, "2026-10-10T15:00:00Z");

        var listed = (ListActivity.Result.Listed) list.execute(query(ADMIN, Optional.of(ActivityArea.STOCK),
            Optional.of(LocalDate.of(2026, 10, 1)), Optional.of(LocalDate.of(2026, 10, 15)), 0));

        assertThat(listed.entries()).containsExactly(lastMinuteOfOct15, firstMinuteOfOct1);
        assertThat(listed.entries()).doesNotContain(lastMinuteOfSept30, firstMinuteOfOct16, couponOnOct10);
        assertThat(listed.hasMore()).isFalse();
    }

    @Test
    void given51Entries_whenTheFirstPageIsListed_then50ComeWithMore_andTheSecondHasTheOldest() {
        List<RecordedActivity> entries = IntStream.range(0, 51)
            .mapToObj(i -> saved(ActivityAction.REVIEW_APPROVED, NOW.minusSeconds(60L * (51 - i)).toString()))
            .toList();

        var first = (ListActivity.Result.Listed) list.execute(
            query(ADMIN, Optional.empty(), Optional.empty(), Optional.empty(), 0));
        var second = (ListActivity.Result.Listed) list.execute(
            query(ADMIN, Optional.empty(), Optional.empty(), Optional.empty(), 1));

        assertThat(first.entries()).hasSize(50).first().isEqualTo(entries.getLast());
        assertThat(first.hasMore()).isTrue();
        assertThat(second.entries()).containsExactly(entries.getFirst());
        assertThat(second.hasMore()).isFalse();
    }

    // --- Retention ---

    @Test
    void givenEntriesOfTwoYearsAndOneDayAgoAndOfOneYearAgo_whenPurged_thenOnlyTheOlderGoes() {
        saved(ActivityAction.COUPON_CREATED, "2024-10-19T15:00:00Z");
        RecordedActivity oneYearAgo = saved(ActivityAction.COUPON_CREATED, "2025-10-20T15:00:00Z");

        assertThat(purge.execute().removed()).isEqualTo(1);
        assertThat(store.all()).containsExactly(oneYearAgo);
    }

    @Test
    void givenAnEntryOfExactlyTwoYearsAgo_whenPurged_thenItIsKept() {
        RecordedActivity exactly = saved(ActivityAction.COUPON_CREATED, "2024-10-20T15:00:00Z");

        assertThat(purge.execute().removed()).isZero();
        assertThat(store.all()).containsExactly(exactly);
    }

    private RecordedActivity saved(ActivityAction action, String occurredAt) {
        RecordedActivity activity = new RecordedActivity(UUID.randomUUID(), Instant.parse(occurredAt),
            new ActivityEntry(ADMIN_ID, action, ActivitySubject.of(SubjectType.SHIPPING)));
        store.save(activity);
        return activity;
    }

    private static ListActivity.Query query(Actor actor, Optional<ActivityArea> area, Optional<LocalDate> from,
                                            Optional<LocalDate> to, int page) {
        return new ListActivity.Query(actor, area, from, to, page);
    }
}
