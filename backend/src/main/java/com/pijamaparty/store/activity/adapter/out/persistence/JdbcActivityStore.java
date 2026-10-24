package com.pijamaparty.store.activity.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityArea;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.application.ActivityPages;
import com.pijamaparty.store.activity.application.DeleteOldActivity;
import com.pijamaparty.store.activity.application.RecordedActivity;
import com.pijamaparty.store.activity.application.SaveActivity;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The activity log in table {@code activity_entry} (V41), in the caller's transaction. Row Level Security decides who
 * writes and reads it (RNF-27): the administrator records its own changes and reads every entry; the system records
 * the entries of signing in and deletes the ones past 2 years. The changed fields go in one {@code jsonb} array.
 */
@Repository
class JdbcActivityStore implements SaveActivity, ActivityPages, DeleteOldActivity {

    private static final String INSERT = """
        INSERT INTO activity_entry (id, occurred_at, actor_id, area, action, subject_type, subject_id, subject_label,
                                    changes)
        VALUES (:id, :occurredAt, :actorId, :area, :action, :subjectType, :subjectId, :subjectLabel,
                CAST(:changes AS jsonb))
        """;

    // Each filter is optional: a null parameter turns its condition off, so the text stays constant (RNF-28).
    private static final String PAGE = """
        SELECT id, occurred_at, actor_id, action, subject_type, subject_id, subject_label, changes
        FROM activity_entry
        WHERE (CAST(:area AS text) IS NULL OR area = CAST(:area AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR occurred_at >= CAST(:from AS timestamptz))
          AND (CAST(:before AS timestamptz) IS NULL OR occurred_at < CAST(:before AS timestamptz))
        ORDER BY occurred_at DESC, id DESC
        OFFSET :offset LIMIT :limit
        """;

    private static final String DELETE_BEFORE = """
        DELETE FROM activity_entry
        WHERE occurred_at < :cutoff
        """;

    private static final TypeReference<List<Map<String, String>>> CHANGES = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcActivityStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void save(RecordedActivity activity) {
        ActivityEntry entry = activity.entry();
        jdbc.update(INSERT, new MapSqlParameterSource("id", activity.id())
            .addValue("occurredAt", activity.occurredAt().atOffset(ZoneOffset.UTC))
            .addValue("actorId", entry.administrator().value())
            .addValue("area", entry.action().area().name())
            .addValue("action", entry.action().name())
            .addValue("subjectType", entry.subject().type().name())
            .addValue("subjectId", entry.subject().id().orElse(null), Types.OTHER)
            .addValue("subjectLabel", entry.subject().label().orElse(null), Types.VARCHAR)
            .addValue("changes", write(entry.changes())));
    }

    @Override
    public List<RecordedActivity> page(Optional<ActivityArea> area, Optional<Instant> from, Optional<Instant> before,
                                       int offset, int limit) {
        return jdbc.query(PAGE, new MapSqlParameterSource()
            .addValue("area", area.map(Enum::name).orElse(null), Types.VARCHAR)
            .addValue("from", from.map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("before", before.map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("offset", offset)
            .addValue("limit", limit), (row, n) -> activity(row));
    }

    @Override
    public int deleteOccurredBefore(Instant cutoff) {
        return jdbc.update(DELETE_BEFORE, new MapSqlParameterSource("cutoff", cutoff.atOffset(ZoneOffset.UTC)));
    }

    private RecordedActivity activity(ResultSet row) throws SQLException {
        return new RecordedActivity(row.getObject("id", UUID.class),
            row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
            new ActivityEntry(UserId.of(row.getObject("actor_id", UUID.class)),
                ActivityAction.valueOf(row.getString("action")),
                new ActivitySubject(SubjectType.valueOf(row.getString("subject_type")),
                    Optional.ofNullable(row.getObject("subject_id", UUID.class)),
                    Optional.ofNullable(row.getString("subject_label"))),
                read(row.getString("changes"))));
    }

    private String write(List<FieldChange> changes) {
        List<Map<String, String>> rows = changes.stream().map(change -> {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("field", change.field());
            fields.put("before", change.before().orElse(null));
            fields.put("after", change.after().orElse(null));
            return fields;
        }).toList();
        try {
            return json.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A list of string maps is always JSON", e);
        }
    }

    private List<FieldChange> read(String changes) {
        try {
            return json.readValue(changes, CHANGES).stream()
                .map(fields -> FieldChange.of(fields.get("field"), fields.get("before"), fields.get("after")))
                .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored changes are a JSON array of string maps", e);
        }
    }
}
