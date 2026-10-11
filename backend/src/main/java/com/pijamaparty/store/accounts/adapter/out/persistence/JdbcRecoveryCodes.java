package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.RecoveryCodes;
import com.pijamaparty.store.accounts.domain.RecoveryCodeHash;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code admin_recovery_code} (V11), in the caller's transaction: the sign-in's {@code SYSTEM} one, the only role its
 * Row Level Security lets in. A code is consumed by a conditional update, so two uses of one code cannot both win.
 */
@Repository
class JdbcRecoveryCodes implements RecoveryCodes {

    private static final String DELETE_ALL = """
        DELETE FROM admin_recovery_code WHERE user_id = :userId
        """;

    private static final String INSERT = """
        INSERT INTO admin_recovery_code (user_id, code_hash, created_at) VALUES (:userId, :codeHash, now())
        """;

    private static final String CONSUME = """
        UPDATE admin_recovery_code SET used_at = :now
        WHERE user_id = :userId AND code_hash = :codeHash AND used_at IS NULL
        """;

    private static final String UNUSED = """
        SELECT count(*) FROM admin_recovery_code WHERE user_id = :userId AND used_at IS NULL
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcRecoveryCodes(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replace(UserId user, List<RecoveryCodeHash> codes) {
        deleteAll(user);
        MapSqlParameterSource[] rows = codes.stream()
            .map(code -> new MapSqlParameterSource("userId", user.value()).addValue("codeHash", code.bytes()))
            .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT, rows);
    }

    @Override
    public boolean consume(UserId user, RecoveryCodeHash code, Instant now) {
        var parameters = new MapSqlParameterSource("userId", user.value())
            .addValue("codeHash", code.bytes())
            .addValue("now", Timestamp.from(now));
        return jdbc.update(CONSUME, parameters) == 1;
    }

    @Override
    public int unused(UserId user) {
        Integer count = jdbc.queryForObject(UNUSED, new MapSqlParameterSource("userId", user.value()), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public void deleteAll(UserId user) {
        jdbc.update(DELETE_ALL, new MapSqlParameterSource("userId", user.value()));
    }
}
