package com.pijamaparty.store.backoffice.adapter.out.persistence;

import com.pijamaparty.store.backoffice.application.LoadOmittedSetupItems;
import com.pijamaparty.store.backoffice.application.SaveOmittedSetupItems;
import com.pijamaparty.store.backoffice.domain.SetupItem;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-46: the setup guide's omitted items in table {@code setup_guide_omission} (V46), one row per item, in the
 * caller's transaction. Omitting twice keeps the first row; showing an item not omitted deletes nothing.
 */
@Repository
class JdbcSetupGuideOmissions implements LoadOmittedSetupItems, SaveOmittedSetupItems {

    private static final String SELECT = "SELECT item FROM setup_guide_omission";

    private static final String INSERT = """
        INSERT INTO setup_guide_omission (item, omitted_at) VALUES (:item, :at)
        ON CONFLICT (item) DO NOTHING
        """;

    private static final String DELETE = "DELETE FROM setup_guide_omission WHERE item = :item";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSetupGuideOmissions(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SetupItem> omitted() {
        Set<SetupItem> items = EnumSet.noneOf(SetupItem.class);
        jdbc.query(SELECT, new MapSqlParameterSource(), row -> {
            items.add(SetupItem.valueOf(row.getString("item")));
        });
        return items;
    }

    @Override
    public void omit(SetupItem item, Instant at) {
        jdbc.update(INSERT, new MapSqlParameterSource("item", item.name()).addValue("at", at.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public void show(SetupItem item) {
        jdbc.update(DELETE, new MapSqlParameterSource("item", item.name()));
    }
}
