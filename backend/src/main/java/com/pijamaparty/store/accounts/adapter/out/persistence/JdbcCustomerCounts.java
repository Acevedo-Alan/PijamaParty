package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import com.pijamaparty.store.accounts.application.CountCustomers;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-33 (T-E8-14): the customers of {@code app_user}, counted in the caller's transaction: Row Level Security shows
 * the administrator the customers (V44) and its own user, which the role condition leaves out. Constant SQL (RNF-28).
 */
@Repository
class JdbcCustomerCounts implements CountCustomers {

    private static final String COUNTS = """
        SELECT count(*) AS registered, count(*) FILTER (WHERE created_at >= :since) AS registered_since
        FROM app_user
        WHERE role = 'CUSTOMER'
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCustomerCounts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Counts count(Instant since) {
        var params = new MapSqlParameterSource("since", since.atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject(COUNTS, params, (row, n) -> new Counts(row.getLong("registered"),
            row.getLong("registered_since")));
    }
}
