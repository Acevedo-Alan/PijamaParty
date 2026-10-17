package com.pijamaparty.store.ordering.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_TRACKING;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E4-08 (UC-22, RNF-27): {@code orders_status_update_guard} as widened by V33. Only the system links a guest order,
 * only to the verified customer of the order's email, on an order never unlinked, changing nothing else; buyers and
 * the administrator never link one, and nobody changes {@code unlinked_at} yet. Every transaction is committed by
 * the application role.
 */
@ApiTest
class GuestOrderLinkRowLevelSecurityTest {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String LINK = "update orders set user_id = ?, updated_at = now() where id = ?";
    private static final String GUARD = "only the system links a guest order";

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final OrderId guestsOrder = OrderId.of(UUID.randomUUID());

    GuestOrderLinkRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                       PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** Customer A, verified, has the email of the guest's paid order ({@code ana@example.com}); B is not verified. */
    @BeforeEach
    void setUp() {
        user(CUSTOMER_A_ID, "ana@example.com", true);
        user(CUSTOMER_B_ID, "bruno@example.com", false);
        OrderRows.insert(owner, guestsOrder, OrderStatus.PAID, Instant.now());
        owner.update("update orders set tracking_token_hash = ? where id = ?", GUEST_TRACKING.hash(),
            guestsOrder.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders, address, user_token, "
            + "external_identity, admin_recovery_code, app_user");
    }

    @Test
    void givenTheVerifiedCustomerOfTheOrdersEmail_whenTheSystemLinksTheOrder_thenItIsThatCustomers() {
        int linked = rls.as(SYSTEM, sql -> sql.update(LINK, CUSTOMER_A_ID.value(), guestsOrder.value()));

        assertThat(linked).isOne();
        assertThat(userOf(guestsOrder)).contains(CUSTOMER_A_ID.value());
    }

    @Test
    void givenACustomerNotVerifiedOrUnknownOrWithAnotherEmail_whenTheSystemLinksTheOrder_thenTheGuardRefusesIt() {
        owner.update("update orders set customer_email = 'bruno@example.com' where id = ?", guestsOrder.value());
        assertRefused(SYSTEM, CUSTOMER_B_ID, GUARD);
        assertRefused(SYSTEM, UserId.of(UUID.randomUUID()), GUARD);
        owner.update("update orders set customer_email = 'otra@example.com' where id = ?", guestsOrder.value());
        assertRefused(SYSTEM, CUSTOMER_A_ID, GUARD);
    }

    @Test
    void givenAnOrderUnlinkedByAnAccountDeletion_whenTheSystemLinksIt_thenTheGuardRefusesIt() {
        owner.update("update orders set unlinked_at = now() where id = ?", guestsOrder.value());

        assertRefused(SYSTEM, CUSTOMER_A_ID, GUARD);
    }

    @Test
    void givenAnOrderOfAnotherCustomer_whenTheSystemMovesItToTheVerifiedCustomerOfItsEmail_thenTheGuardRefusesIt() {
        owner.update("update orders set user_id = ? where id = ?", CUSTOMER_B_ID.value(), guestsOrder.value());

        assertRefused(SYSTEM, CUSTOMER_A_ID, GUARD);
    }

    @Test
    void givenALinkThatAlsoChangesTheStatus_whenTheSystemRunsIt_thenTheGuardRefusesIt() {
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(
            "update orders set user_id = ?, status = 'PREPARING' where id = ?", CUSTOMER_A_ID.value(),
            guestsOrder.value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(GUARD);
    }

    @Test
    void givenTheAdministrator_whenItLinksTheOrder_thenTheGuardRefusesIt() {
        assertRefused(ADMIN, CUSTOMER_A_ID, GUARD);
    }

    @Test
    void givenTheBuyers_whenTheyLinkTheGuestsOrder_thenTheirGuardRefusesIt() {
        var holding = Actor.customer(CUSTOMER_A_ID, Optional.empty(), Optional.of(GUEST_TRACKING));
        assertRefused(GUEST, CUSTOMER_A_ID, "a buyer may only update the email");
        assertRefused(holding, CUSTOMER_A_ID, "a buyer may only update the email");
        int reached = rls.as(CUSTOMER_A, sql -> sql.update(LINK, CUSTOMER_A_ID.value(), guestsOrder.value()));
        assertThat(reached).as("without the token a customer does not even reach a guest order").isZero();
    }

    @Test
    void givenTheStaff_whenTheyChangeWhenAnOrderWasUnlinked_thenTheGuardRefusesIt() {
        for (Actor staff : List.of(SYSTEM, ADMIN)) {
            assertThatThrownBy(() -> rls.as(staff, sql -> sql.update(
                "update orders set unlinked_at = now() where id = ?", guestsOrder.value())))
                .as(staff.toString())
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("may only update the status");
        }
    }

    private void assertRefused(Actor actor, UserId customer, String reason) {
        assertThatThrownBy(() -> rls.as(actor, sql -> sql.update(LINK, customer.value(), guestsOrder.value())))
            .as(actor + " linking to " + customer)
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(reason);
        assertThat(userOf(guestsOrder)).isNotEqualTo(Optional.of(customer.value()));
    }

    private Optional<UUID> userOf(OrderId order) {
        return Optional.ofNullable(owner.queryForObject("select user_id from orders where id = ?", UUID.class,
            order.value()));
    }

    private void user(UserId id, String email, boolean verified) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, ?, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, id.value(), email, verified, PASSWORD_HASH);
    }
}
