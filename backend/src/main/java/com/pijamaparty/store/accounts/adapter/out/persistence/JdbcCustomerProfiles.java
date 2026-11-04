package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SignInProvider;
import com.pijamaparty.store.accounts.application.LoadCustomerProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-34 (T-E8-11): a customer of {@code app_user} with its {@code address} rows and the providers of its
 * {@code external_identity} rows, in the caller's transaction: Row Level Security shows the administrator the
 * customers (V44), their addresses (V29) and their linked accounts (V44). Never the external subject or its email.
 * Constant SQL (RNF-28).
 */
@Repository
class JdbcCustomerProfiles implements LoadCustomerProfile {

    private static final String USER = """
        SELECT id, name, email, email_verified, phone, created_at
        FROM app_user
        WHERE id = :id AND role = 'CUSTOMER'
        """;

    // The main one first; the id breaks ties between addresses added at the same instant.
    private static final String ADDRESSES = """
        SELECT id, street, street_number, floor, apartment, city, province, postal_code, is_primary
        FROM address
        WHERE user_id = :id
        ORDER BY is_primary DESC, created_at, id
        """;

    private static final String PROVIDERS = """
        SELECT provider
        FROM external_identity
        WHERE user_id = :id
        ORDER BY created_at, provider
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCustomerProfiles(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CustomerProfile> find(UUID customerId) {
        var params = new MapSqlParameterSource("id", customerId);
        List<User> users = jdbc.query(USER, params, JdbcCustomerProfiles::user);
        if (users.isEmpty()) {
            return Optional.empty();
        }
        User user = users.getFirst();
        List<SavedAddress> addresses = jdbc.query(ADDRESSES, params, JdbcCustomerProfiles::address);
        List<SignInProvider> providers = jdbc.queryForList(PROVIDERS, params, String.class).stream()
            .map(SignInProvider::valueOf).toList();
        return Optional.of(new CustomerProfile(user.id(), user.name(), user.email(), user.emailVerified(),
            user.phone(), user.registeredAt().toInstant(), providers, addresses));
    }

    private static User user(ResultSet row, int rowNumber) throws SQLException {
        return new User(row.getObject("id", UUID.class), row.getString("name"), row.getString("email"),
            row.getBoolean("email_verified"), Optional.ofNullable(row.getString("phone")),
            row.getObject("created_at", OffsetDateTime.class));
    }

    private static SavedAddress address(ResultSet row, int rowNumber) throws SQLException {
        return new SavedAddress(row.getObject("id", UUID.class), row.getString("street"),
            row.getString("street_number"), Optional.ofNullable(row.getString("floor")),
            Optional.ofNullable(row.getString("apartment")), row.getString("city"), row.getString("province"),
            row.getString("postal_code"), row.getBoolean("is_primary"));
    }

    /** Personal data: never logged. */
    private record User(UUID id, String name, String email, boolean emailVerified, Optional<String> phone,
                        OffsetDateTime registeredAt) {

        @Override
        public String toString() {
            return "User[id=" + id + ", ***]";
        }
    }
}
