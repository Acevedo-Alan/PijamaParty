package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import com.pijamaparty.store.accounts.application.CustomerFilter;
import com.pijamaparty.store.accounts.application.CustomerPages;
import com.pijamaparty.store.platform.persistence.LikePattern;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-34 (T-E8-10): the customers in {@code app_user}, in the caller's transaction: Row Level Security shows the
 * administrator the customers (V44) and its own user, which the role condition leaves out.
 */
@Repository
class JdbcCustomerPages implements CustomerPages {

    // Newest first; the id breaks ties. One constant text whose conditions each switch off with a null parameter,
    // never built by concatenation (RNF-28). The pattern is the search with %, _ and \ escaped (LikePattern), folded
    // like the columns. Under a few thousand customers, the plan need not be specific.
    private static final String PAGE = """
        SELECT id, name, email, created_at
        FROM app_user
        WHERE role = 'CUSTOMER'
          AND (CAST(:pattern AS text) IS NULL
               OR lower(unaccent(name)) LIKE lower(unaccent(CAST(:pattern AS text))) ESCAPE '\\'
               OR lower(unaccent(email)) LIKE lower(unaccent(CAST(:pattern AS text))) ESCAPE '\\')
          AND (CAST(:registeredFrom AS timestamptz) IS NULL OR created_at >= CAST(:registeredFrom AS timestamptz))
          AND (CAST(:registeredBefore AS timestamptz) IS NULL OR created_at < CAST(:registeredBefore AS timestamptz))
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCustomerPages(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Customer> page(CustomerFilter filter, int offset, int limit) {
        var params = new MapSqlParameterSource("offset", offset).addValue("limit", limit)
            .addValue("pattern", filter.search().map(search -> LikePattern.contains(search.text())).orElse(null),
                Types.VARCHAR)
            .addValue("registeredFrom", filter.registeredFrom().map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("registeredBefore", filter.registeredBefore().map(at -> at.atOffset(ZoneOffset.UTC))
                .orElse(null), Types.TIMESTAMP_WITH_TIMEZONE);
        return jdbc.query(PAGE, params, JdbcCustomerPages::toCustomer);
    }

    private static Customer toCustomer(ResultSet row, int rowNumber) throws SQLException {
        return new Customer(row.getObject("id", UUID.class), row.getString("name"), row.getString("email"),
            row.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
