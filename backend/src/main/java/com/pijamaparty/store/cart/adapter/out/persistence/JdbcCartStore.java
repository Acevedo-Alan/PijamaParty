package com.pijamaparty.store.cart.adapter.out.persistence;

import com.pijamaparty.store.cart.application.DeleteCart;
import com.pijamaparty.store.cart.application.DeleteIdleCarts;
import com.pijamaparty.store.cart.application.LoadCart;
import com.pijamaparty.store.cart.application.SaveCart;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Carts and their items in tables {@code cart} and {@code cart_item} (V12), in the caller's transaction. Both have
 * Row Level Security, so every query only ever sees the cart of the transaction's actor (RNF-27); the owner in the
 * {@code WHERE} says which cart that is.
 */
@Repository
class JdbcCartStore implements LoadCart, SaveCart, DeleteCart, DeleteIdleCarts {

    // One of :tokenHash and :userId is null, and a comparison with null matches nothing.
    private static final String SELECT_CART = """
        SELECT id, token_hash, user_id, coupon_id, created_at, updated_at
        FROM cart
        WHERE token_hash = :tokenHash OR user_id = :userId
        """;

    private static final String LOCK_CART = SELECT_CART + """
        FOR UPDATE
        """;

    // The id breaks ties between items added at the same instant.
    private static final String SELECT_ITEMS = """
        SELECT id, variant_id, quantity, last_seen_price_cents, created_at
        FROM cart_item
        WHERE cart_id = :cartId
        ORDER BY created_at, id
        """;

    private static final String UPSERT_CART = """
        INSERT INTO cart (id, token_hash, user_id, coupon_id, created_at, updated_at)
        VALUES (:id, :tokenHash, :userId, :couponId, :createdAt, :updatedAt)
        ON CONFLICT (id) DO UPDATE SET coupon_id = excluded.coupon_id, updated_at = excluded.updated_at
        """;

    private static final String DELETE_OTHER_ITEMS = """
        DELETE FROM cart_item
        WHERE cart_id = :cartId AND id <> ALL (CAST(:keep AS uuid[]))
        """;

    private static final String UPSERT_ITEM = """
        INSERT INTO cart_item (id, cart_id, variant_id, quantity, last_seen_price_cents, created_at)
        VALUES (:id, :cartId, :variantId, :quantity, :lastSeenPriceCents, :createdAt)
        ON CONFLICT (id) DO UPDATE
        SET quantity = excluded.quantity, last_seen_price_cents = excluded.last_seen_price_cents
        """;

    // T-E4-04: the customer signing in reaches the guest cart of its cart token (V32); the foreign key of cart_item
    // deletes its items.
    private static final String DELETE_CART = """
        DELETE FROM cart
        WHERE id = :id
        """;

    // T-E2-04: only SYSTEM reaches every cart (RNF-27). cart_updated_at_idx finds them, and the foreign key of
    // cart_item deletes their items.
    private static final String DELETE_IDLE_CARTS = """
        DELETE FROM cart
        WHERE updated_at < :cutoff
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCartStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cart> findByOwner(CartOwner owner) {
        return withItems(jdbc.query(SELECT_CART, owner(owner), JdbcCartStore::row));
    }

    @Override
    public Optional<Cart> lockByOwner(CartOwner owner) {
        return withItems(jdbc.query(LOCK_CART, owner(owner), JdbcCartStore::row));
    }

    @Override
    public void save(Cart cart) {
        jdbc.update(UPSERT_CART, owner(cart.owner())
            .addValue("id", cart.id().value())
            .addValue("couponId", cart.coupon().map(CouponId::value).orElse(null), Types.OTHER)
            .addValue("createdAt", cart.createdAt().atOffset(ZoneOffset.UTC))
            .addValue("updatedAt", cart.updatedAt().atOffset(ZoneOffset.UTC)));
        UUID[] keep = cart.items().stream().map(item -> item.id().value()).toArray(UUID[]::new);
        jdbc.update(DELETE_OTHER_ITEMS, new MapSqlParameterSource("cartId", cart.id().value())
            .addValue("keep", keep, Types.ARRAY));
        SqlParameterSource[] rows = cart.items().stream()
            .map(item -> new MapSqlParameterSource("id", item.id().value())
                .addValue("cartId", cart.id().value())
                .addValue("variantId", item.variantId().value())
                .addValue("quantity", item.quantity())
                .addValue("lastSeenPriceCents", item.lastSeenPrice().cents())
                .addValue("createdAt", item.createdAt().atOffset(ZoneOffset.UTC)))
            .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT_ITEM, rows);
    }

    @Override
    public boolean delete(CartId cartId) {
        return jdbc.update(DELETE_CART, new MapSqlParameterSource("id", cartId.value())) == 1;
    }

    @Override
    public int deleteUpdatedBefore(Instant cutoff) {
        return jdbc.update(DELETE_IDLE_CARTS, new MapSqlParameterSource("cutoff", cutoff.atOffset(ZoneOffset.UTC)));
    }

    private Optional<Cart> withItems(List<CartRow> rows) {
        return rows.stream().findFirst()
            .map(row -> Cart.of(row.id(), row.owner(), items(row.id()), row.coupon(), row.createdAt(),
                row.updatedAt()));
    }

    private List<CartItem> items(CartId cartId) {
        return jdbc.query(SELECT_ITEMS, new MapSqlParameterSource("cartId", cartId.value()), (row, n) -> new CartItem(
            CartItemId.of(row.getObject("id", UUID.class)),
            VariantId.of(row.getObject("variant_id", UUID.class)),
            row.getInt("quantity"),
            Money.of(row.getLong("last_seen_price_cents")),
            instant(row, "created_at")));
    }

    private static MapSqlParameterSource owner(CartOwner owner) {
        var params = new MapSqlParameterSource();
        switch (owner) {
            case CartOwner.Guest guest -> params
                .addValue("tokenHash", guest.tokenHash(), Types.VARCHAR)
                .addValue("userId", null, Types.OTHER);
            case CartOwner.Customer customer -> params
                .addValue("tokenHash", null, Types.VARCHAR)
                .addValue("userId", customer.userId().value(), Types.OTHER);
        }
        return params;
    }

    private static CartRow row(ResultSet row, int rowNumber) throws SQLException {
        String tokenHash = row.getString("token_hash");
        CartOwner owner = tokenHash != null
            ? new CartOwner.Guest(tokenHash)
            : new CartOwner.Customer(UserId.of(row.getObject("user_id", UUID.class)));
        Optional<CouponId> coupon = Optional.ofNullable(row.getObject("coupon_id", UUID.class)).map(CouponId::of);
        return new CartRow(CartId.of(row.getObject("id", UUID.class)), owner, coupon, instant(row, "created_at"),
            instant(row, "updated_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record CartRow(CartId id, CartOwner owner, Optional<CouponId> coupon, Instant createdAt,
                           Instant updatedAt) {}
}
