package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link CatalogActivity}: every entry recorded is read back whole. */
public abstract class CatalogActivityContractTest<T extends CatalogActivity> extends ContractTest<T> {

    /** The entries recorded so far. */
    protected abstract List<ActivityEntry> recorded();

    @Test
    void givenAnEntryWithChanges_whenRecorded_thenItIsReadBackWhole() {
        ActivityEntry entry = new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.VARIANT_UPDATED,
            ActivitySubject.of(SubjectType.VARIANT, UUID.randomUUID(), "PP-AB12CD34"),
            List.of(FieldChange.of("price", "$20.000", "$22.000"), FieldChange.of("active", "true", "false")));

        subject.record(entry);

        assertThat(recorded()).containsExactly(entry);
    }

    @Test
    void givenTwoEntries_whenRecorded_thenBothAreKept() {
        ActivityEntry created = new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.CATEGORY_CREATED,
            ActivitySubject.of(SubjectType.CATEGORY, UUID.randomUUID(), "Verano"),
            List.of(FieldChange.of("name", null, "Verano")));
        ActivityEntry reordered = new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.CATEGORIES_REORDERED,
            ActivitySubject.of(SubjectType.CATEGORY), List.of(FieldChange.of("order", "Verano, Invierno",
                "Invierno, Verano")));

        subject.record(created);
        subject.record(reordered);

        assertThat(recorded()).containsExactlyInAnyOrder(created, reordered);
    }
}
