package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.CustomerOrderPages;
import com.pijamaparty.store.ordering.application.CustomerOrderSummary;
import com.pijamaparty.store.ordering.application.CustomerOrderUnlinks;
import com.pijamaparty.store.ordering.application.CustomerOrderUnlinksContractTest;
import com.pijamaparty.store.ordering.application.GuestOrderLinks;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Orders placed by their buyers and unlinked as the account deletion does it: in one committed transaction as the
 * customer, which first deletes its own user and then unlinks its orders, so Row Level Security, the function and the
 * buyer guard of V36 apply as in production (RNF-27). Linked again as {@code SYSTEM} and read back as the customer.
 */
@ApiTest
class JdbcCustomerOrderUnlinksContractTest
    extends CustomerOrderUnlinksContractTest<JdbcCustomerOrderUnlinksContractTest.AsCustomer> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String GUARD = "a customer may only unlink its own orders, once its account is deleted";

    private final AsCustomer asCustomer;
    private final JdbcTemplate owner;
    private final RlsPolicyTester rls;

    JdbcCustomerOrderUnlinksContractTest(JdbcOrderStore adapter, JdbcTemplate jdbc,
                                         PlatformTransactionManager transactionManager,
                                         PostgreSQLContainer<?> postgres) {
        this.asCustomer = new AsCustomer(adapter, jdbc, new TransactionTemplate(transactionManager),
            new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
    }

    @Override
    protected AsCustomer createSubject() {
        return asCustomer;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asCustomer.tokens().put(token.hash(), token);
    }

    @Override
    protected UserId verifiedCustomer(String email) {
        UserId customer = UserId.of(UUID.randomUUID());
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, customer.value(), email, PASSWORD_HASH);
        return customer;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders, address, user_token, "
            + "external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenAnOrderInAnyStatus_whenUnlinked_thenOnlyItsUserItsUnlinkingAndUpdateInstantsChange() {
        UserId ana = verifiedCustomer(ANA);
        var order = placed(Optional.of(ana), ANA);
        owner.update("""
            update orders set status = 'DELIVERED', delivered_at = now(), shipment_tracking_number = 'TN123456789AR'
            where id = ?
            """, order.id().value());
        Map<String, Object> before = rowOf(order.id());

        assertThat(asCustomer.unlinkAll(ana, NOW)).isOne();

        Map<String, Object> after = rowOf(order.id());
        assertThat(after.get("user_id")).isNull();
        assertThat(((Timestamp) after.get("unlinked_at")).toInstant()).isEqualTo(NOW);
        assertThat(((Timestamp) after.get("updated_at")).toInstant()).isEqualTo(NOW);
        before.keySet().removeAll(List.of("user_id", "unlinked_at", "updated_at"));
        after.keySet().removeAll(List.of("user_id", "unlinked_at", "updated_at"));
        assertThat(after).isEqualTo(before);
    }

    @Test
    void givenACustomerWhoseAccountStillExists_whenUnlinkingItsOrders_thenTheFunctionRefusesIt() {
        UserId ana = verifiedCustomer(ANA);
        var order = placed(Optional.of(ana), ANA);

        assertThatThrownBy(() -> asCustomer.as(ana, () -> asCustomer.adapter().unlinkAll(ana, NOW)))
            .hasStackTraceContaining(GUARD);
        assertThat(rowOf(order.id()).get("user_id")).isEqualTo(ana.value());
    }

    @Test
    void givenADeletedCustomer_whenItUpdatesItsOrdersItself_thenNoPolicyLetsItChangeAny() {
        UserId ana = verifiedCustomer(ANA);
        var order = placed(Optional.of(ana), ANA);

        int updated = asCustomer.as(ana, () -> {
            asCustomer.deleteUser(ana);
            return asCustomer.jdbc().update("""
                update orders set user_id = null, unlinked_at = now(), national_id = '99999999'
                where user_id = ?
                """, ana.value());
        });

        assertThat(updated).isZero();
        assertThat(rowOf(order.id())).containsEntry("user_id", ana.value()).containsEntry("national_id", "12345678");
    }

    @Test
    void givenAnotherCustomersOrders_whenADeletedCustomerUnlinksThem_thenTheFunctionRefusesIt() {
        UserId ana = verifiedCustomer(ANA);
        UserId bruno = verifiedCustomer("bruno@example.com");
        var brunos = placed(Optional.of(bruno), "bruno@example.com");

        assertThatThrownBy(() -> asCustomer.as(ana, () -> {
            asCustomer.deleteUser(ana);
            return asCustomer.adapter().unlinkAll(bruno, NOW);
        })).hasStackTraceContaining(GUARD);
        assertThat(rowOf(brunos.id()).get("user_id")).isEqualTo(bruno.value());
    }

    static Stream<Arguments> everyoneButTheDeletedCustomer() {
        return Stream.of(
            Arguments.of("another customer", Optional.of(RlsPolicyTester.CUSTOMER_B)),
            Arguments.of("guest", Optional.of(RlsPolicyTester.GUEST)),
            Arguments.of("administrator", Optional.of(RlsPolicyTester.ADMIN)),
            Arguments.of("system", Optional.of(RlsPolicyTester.SYSTEM)),
            Arguments.of("no context", Optional.empty()));
    }

    /** The function bypasses Row Level Security, so it alone must refuse every other context (RNF-27). */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyoneButTheDeletedCustomer")
    void givenADeletedCustomersOrder_whenAnyoneElseUnlinksIt_thenTheFunctionRefusesItAndNothingChanges(
        String name, Optional<Actor> actor) {
        UserId ana = verifiedCustomer(ANA);
        var order = placed(Optional.of(ana), ANA);
        owner.update("delete from app_user where id = ?", ana.value());
        Function<JdbcTemplate, Integer> unlink = sql -> sql.queryForObject(
            "select unlink_deleted_customer_orders(?, now())", Integer.class, ana.value());

        assertThatThrownBy(() -> actor.map(caller -> rls.as(caller, unlink))
                .orElseGet(() -> rls.withoutContext(unlink)))
            .hasStackTraceContaining(GUARD);
        assertThat(rowOf(order.id())).containsEntry("user_id", ana.value()).containsEntry("unlinked_at", null);
    }

    private Map<String, Object> rowOf(OrderId order) {
        return owner.queryForMap("select * from orders where id = ?", order.value());
    }

    /**
     * Each call in its own committed transaction: inserts as the buyer, unlinks as the deleted customer, links as the
     * system, reads as the customer.
     */
    record AsCustomer(JdbcOrderStore adapter, JdbcTemplate jdbc, TransactionTemplate transactions,
                      Map<String, TrackingToken> tokens)
        implements SaveOrders, OrderNumbers, CustomerOrderUnlinks, CustomerOrderPages, GuestOrderLinks {

        /** As the account deletion: the user goes first, in the same transaction (V36). */
        @Override
        public int unlinkAll(UserId customer, Instant at) {
            return as(customer, () -> {
                deleteUser(customer);
                return adapter.unlinkAll(customer, at);
            });
        }

        @Override
        public int link(UserId customer, String email, Instant at) {
            Integer linked = CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.link(customer, email, at)));
            return linked == null ? 0 : linked;
        }

        @Override
        public List<CustomerOrderSummary> page(UserId customer, int offset, int limit) {
            return as(customer, () -> adapter.page(customer, offset, limit));
        }

        @Override
        public void insert(NewOrder order) {
            TrackingToken token = tokens.get(order.trackingTokenHash());
            Actor buyer = order.userId()
                .map(user -> Actor.customer(user, Optional.empty(), Optional.of(token)))
                .orElseGet(() -> Actor.anonymous(Optional.empty(), Optional.of(token)));
            CurrentActor.runAs(buyer, () -> transactions.executeWithoutResult(status -> adapter.insert(order)));
        }

        @Override
        public long next() {
            Long number = CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.next()));
            return number == null ? 0 : number;
        }

        /** The customer deletes its own user, as V36 lets it, in the caller's transaction. */
        void deleteUser(UserId customer) {
            jdbc.update("delete from app_user where id = ?", customer.value());
        }

        <R> R as(UserId customer, Supplier<R> work) {
            Actor actor = Actor.customer(customer, Optional.empty(), Optional.empty());
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }
    }
}
