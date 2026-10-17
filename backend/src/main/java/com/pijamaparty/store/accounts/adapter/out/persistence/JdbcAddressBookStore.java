package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.LoadAddressBook;
import com.pijamaparty.store.accounts.application.SaveAddressBook;
import com.pijamaparty.store.accounts.domain.Address;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressDetails;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * UC-20: the address book in table {@code address} (V29), in the caller's transaction. Row Level Security shows a
 * customer only its own addresses and its own account (RNF-27); the {@code WHERE} says whose book it is.
 */
@Repository
class JdbcAddressBookStore implements LoadAddressBook, SaveAddressBook {

    // The account row is the lock of its book: the addresses have no row to lock before the first one exists.
    private static final String LOCK_ACCOUNT = """
        SELECT id
        FROM app_user
        WHERE id = :userId
        FOR UPDATE
        """;

    // The id breaks ties between addresses added at the same instant.
    private static final String SELECT_ADDRESSES = """
        SELECT id, street, street_number, floor, apartment, city, province, postal_code, is_primary, created_at,
               updated_at
        FROM address
        WHERE user_id = :userId
        ORDER BY created_at, id
        """;

    private static final String DELETE_OTHER_ADDRESSES = """
        DELETE FROM address
        WHERE user_id = :userId AND id <> ALL (CAST(:keep AS uuid[]))
        """;

    private static final String UPSERT_ADDRESS = """
        INSERT INTO address (id, user_id, street, street_number, floor, apartment, city, province, postal_code,
                             is_primary, created_at, updated_at)
        VALUES (:id, :userId, :street, :number, :floor, :apartment, :city, :province, :postalCode, :primary,
                :createdAt, :updatedAt)
        ON CONFLICT (id) DO UPDATE
        SET street = excluded.street, street_number = excluded.street_number, floor = excluded.floor,
            apartment = excluded.apartment, city = excluded.city, province = excluded.province,
            postal_code = excluded.postal_code, is_primary = excluded.is_primary, updated_at = excluded.updated_at
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAddressBookStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AddressBook findByCustomer(UserId customer) {
        return AddressBook.of(customer, addresses(customer));
    }

    @Override
    public Optional<AddressBook> lockByCustomer(UserId customer) {
        if (jdbc.queryForList(LOCK_ACCOUNT, userId(customer), UUID.class).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(findByCustomer(customer));
    }

    @Override
    public void save(AddressBook book) {
        UUID[] keep = book.addresses().stream().map(address -> address.id().value()).toArray(UUID[]::new);
        jdbc.update(DELETE_OTHER_ADDRESSES, userId(book.owner()).addValue("keep", keep, Types.ARRAY));
        // The unique index of the primary is checked at the end of each statement: the previous primary is unmarked
        // before the new one is marked.
        SqlParameterSource[] rows = book.addresses().stream()
            .sorted(Comparator.comparing(Address::primary))
            .map(address -> row(book.owner(), address))
            .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT_ADDRESS, rows);
    }

    private List<Address> addresses(UserId customer) {
        return jdbc.query(SELECT_ADDRESSES, userId(customer), JdbcAddressBookStore::address);
    }

    private static MapSqlParameterSource userId(UserId customer) {
        return new MapSqlParameterSource("userId", customer.value());
    }

    private static SqlParameterSource row(UserId owner, Address address) {
        AddressDetails details = address.details();
        return userId(owner)
            .addValue("id", address.id().value())
            .addValue("street", details.street())
            .addValue("number", details.number())
            .addValue("floor", details.floor().orElse(null), Types.VARCHAR)
            .addValue("apartment", details.apartment().orElse(null), Types.VARCHAR)
            .addValue("city", details.city())
            .addValue("province", details.province().name())
            .addValue("postalCode", details.postalCode())
            .addValue("primary", address.primary())
            .addValue("createdAt", address.createdAt().atOffset(ZoneOffset.UTC))
            .addValue("updatedAt", address.updatedAt().atOffset(ZoneOffset.UTC));
    }

    private static Address address(ResultSet row, int rowNumber) throws SQLException {
        var details = new AddressDetails(row.getString("street"), row.getString("street_number"),
            Optional.ofNullable(row.getString("floor")), Optional.ofNullable(row.getString("apartment")),
            row.getString("city"), Province.valueOf(row.getString("province")), row.getString("postal_code"));
        return new Address(AddressId.of(row.getObject("id", UUID.class)), details, row.getBoolean("is_primary"),
            row.getObject("created_at", OffsetDateTime.class).toInstant(),
            row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
