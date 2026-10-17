package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.DeleteCustomers;
import com.pijamaparty.store.platform.domain.UserId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-28 (T-E4-16): deletes a customer's {@code app_user} row in the caller's transaction, which runs as that customer:
 * Row Level Security lets it delete only its own, and only a customer's (V36). Its addresses, tokens, external
 * identities and waiting link go with it ({@code ON DELETE CASCADE}).
 */
@Repository
class JdbcCustomerDeletions implements DeleteCustomers {

    private static final String DELETE_CUSTOMER = """
        DELETE FROM app_user
        WHERE id = :userId AND role = 'CUSTOMER'
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCustomerDeletions(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean delete(UserId customer) {
        return jdbc.update(DELETE_CUSTOMER, new MapSqlParameterSource("userId", customer.value())) == 1;
    }
}
