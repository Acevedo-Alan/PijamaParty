package com.pijamaparty.store.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-E6-18 (RNF-27): every table of the schema is either protected by Row Level Security or public on purpose. A new
 * table fails here until it is added to one of the two lists, with its reason in
 * {@code docs/use-cases/E6-hardening-and-release.md}.
 */
@ApiTest
class RowLevelSecurityCoverageTest {

    /** Customer data or internal use: RLS enabled and forced, so not even a forgotten filter leaks a row. */
    private static final Set<String> PROTECTED = Set.of(
        "orders", "order_item", "order_tracking_token", "payment", "payment_event", "payment_checkout", "address",
        "app_user", "admin_recovery_code", "cart", "cart_item", "review", "idempotency_key", "email_outbox",
        "email_event", "external_identity", "user_token", "pending_link", "withdrawal_request");

    /** Without customer data, or out of RLS's reach with the reason given in RNF-27. */
    private static final Set<String> PUBLIC = Set.of(
        // The catalog and the store's settings: public, without personal data
        "category", "product", "variant", "product_image", "size_guide", "stock_level", "shipping_zone_rate",
        "shop_setting",
        // Coupons and their uses, by order id only
        "coupon", "coupon_scope", "coupon_redemption",
        // Quantities and the administrator's note, by variant, order and administrator id
        "stock_movement",
        // Spring Session works outside the transaction with the security context; the principal is a UUID
        "spring_session", "spring_session_attributes",
        // Domain events carry ids and non-personal values only (ArchitectureTest)
        "event_publication", "event_publication_archive",
        // The application has no privilege on it (DatabaseRolesTest)
        "flyway_schema_history");

    private final JdbcTemplate jdbc;

    RowLevelSecurityCoverageTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void givenTheSchema_whenItsTablesAreListed_thenEachIsProtectedOrPublicOnPurpose() {
        List<String> tables = jdbc.queryForList("""
            select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
             where n.nspname = 'public' and c.relkind in ('r', 'p')""", String.class);

        Set<String> unclassified = new TreeSet<>(tables);
        unclassified.removeAll(PROTECTED);
        unclassified.removeAll(PUBLIC);
        assertThat(unclassified).as("tables on neither list").isEmpty();
        assertThat(tables).as("every listed table exists").containsAll(PROTECTED).containsAll(PUBLIC);
    }

    @Test
    void givenTheTablesWithCustomerData_whenTheirFlagsAreRead_thenRlsIsEnabledAndForcedOnExactlyThose() {
        List<String> forced = jdbc.queryForList("""
            select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
             where n.nspname = 'public' and c.relkind in ('r', 'p')
               and c.relrowsecurity and c.relforcerowsecurity""", String.class);
        List<String> enabledOnly = jdbc.queryForList("""
            select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
             where n.nspname = 'public' and c.relkind in ('r', 'p')
               and c.relrowsecurity <> c.relforcerowsecurity""", String.class);

        assertThat(forced).containsExactlyInAnyOrderElementsOf(PROTECTED);
        assertThat(enabledOnly).as("enabled without FORCE, or the reverse").isEmpty();
    }
}
