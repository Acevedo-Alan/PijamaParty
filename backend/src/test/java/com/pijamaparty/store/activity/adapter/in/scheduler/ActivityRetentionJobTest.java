package com.pijamaparty.store.activity.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** D-E8-11 (T-E8-01): the daily job deletes, as the system and through Row Level Security, the entries past 2 years. */
@ApiTest
class ActivityRetentionJobTest {

    private final ActivityRetentionJob job;
    private final JdbcTemplate owner;

    ActivityRetentionJobTest(ActivityRetentionJob job, PostgreSQLContainer<?> postgres) {
        this.job = job;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate activity_entry");
    }

    @Test
    void givenEntriesOfTwoYearsAndOneDayAgoAndOfOneYearAgo_whenTheJobRuns_thenOnlyTheOlderIsDeleted() {
        entry("2 years 1 day");
        UUID oneYearOld = entry("1 year");

        job.run();

        assertThat(owner.queryForList("select id from activity_entry", UUID.class)).containsExactly(oneYearOld);
    }

    @Test
    void givenTheJobRuns_whenItEnds_thenNoActorIsLeftBoundToTheThread() {
        job.run();

        assertThat(CurrentActor.get()).isEmpty();
    }

    private UUID entry(String age) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into activity_entry (id, occurred_at, actor_id, area, action, subject_type, changes)
            values (?, now() - cast(? as interval), ?, 'PROMOTIONS', 'COUPON_CREATED', 'COUPON', '[]'::jsonb)
            """, id, age, UUID.randomUUID());
        return id;
    }
}
