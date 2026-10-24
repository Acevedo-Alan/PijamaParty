package com.pijamaparty.store.activity.testing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.UserId;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The rows of {@code activity_entry} as the table owner reads them, past Row Level Security, for the contract tests of
 * the adapters over {@code activity.api.ActivityLog}.
 */
public final class ActivityRows {

    private final JdbcTemplate owner;
    private final ObjectMapper json;

    public ActivityRows(PostgreSQLContainer<?> postgres, ObjectMapper json) {
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.json = json;
    }

    public List<ActivityEntry> all() {
        return owner.query("""
            select actor_id, action, subject_type, subject_id, subject_label, changes::text as changes
            from activity_entry
            """, (row, index) -> new ActivityEntry(UserId.of(row.getObject("actor_id", UUID.class)),
            ActivityAction.valueOf(row.getString("action")),
            new ActivitySubject(SubjectType.valueOf(row.getString("subject_type")),
                Optional.ofNullable(row.getObject("subject_id", UUID.class)),
                Optional.ofNullable(row.getString("subject_label"))),
            changes(row.getString("changes"))));
    }

    public void deleteAll() {
        owner.execute("truncate activity_entry");
    }

    private List<FieldChange> changes(String text) {
        try {
            List<FieldChange> changes = new ArrayList<>();
            for (JsonNode change : json.readTree(text)) {
                changes.add(new FieldChange(change.get("field").asText(), value(change.get("before")),
                    value(change.get("after"))));
            }
            return changes;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<String> value(JsonNode node) {
        return node == null || node.isNull() ? Optional.empty() : Optional.of(node.asText());
    }
}
