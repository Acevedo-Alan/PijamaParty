package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import com.pijamaparty.store.ordering.application.ActiveWithdrawalViews;
import com.pijamaparty.store.ordering.application.AdminWithdrawalsContractTest;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.application.WithdrawalPages;
import com.pijamaparty.store.ordering.application.WithdrawalResolutions;
import com.pijamaparty.store.ordering.application.WithdrawalResolutions.LockedWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawalSummary;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Orders and their requests stored as the owner, as the form leaves them; listed, viewed and resolved by the
 * administrator, each call in its own committed transaction, so Row Level Security applies as in production (RNF-27).
 * The policies for every actor come in T-E3-27.
 */
@ApiTest
class JdbcAdminWithdrawalsContractTest extends AdminWithdrawalsContractTest<JdbcAdminWithdrawalsContractTest.AsAdmin> {

    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    JdbcAdminWithdrawalsContractTest(JdbcWithdrawalStore adapter, PlatformTransactionManager transactionManager,
                                     PostgreSQLContainer<?> postgres) {
        this.asAdmin = new AsAdmin(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAdmin createSubject() {
        return asAdmin;
    }

    @Override
    protected WithdrawableOrder placed(OrderStatus status) {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, status, PLACED_AT.plusSeconds(30 * 60));
        return new WithdrawableOrder(order, OrderRows.numberOf(owner, order), status, PLACED_AT, Optional.empty());
    }

    @Override
    protected void requested(NewWithdrawal withdrawal) {
        owner.update("insert into withdrawal_request (id, order_id, code, status, out_of_term, created_at) "
                + "values (?, ?, ?, 'RECEIVED', ?, ?)", withdrawal.id(), withdrawal.order().value(),
            withdrawal.code().value(), withdrawal.outOfTerm(), withdrawal.createdAt().atOffset(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenARequestLockedForResolution_whenAnotherTransactionLocksItWithoutWaiting_thenItIsHeld() {
        NewWithdrawal withdrawal = new NewWithdrawal(UUID.randomUUID(), placed(OrderStatus.PAID).id(),
            new WithdrawalCode("ARR-7K2M9QXZ"), false, NOW);
        requested(withdrawal);

        asAdmin.inOneTransaction(() -> {
            assertThat(asAdmin.adapter().lockForResolution(withdrawal.id())).isPresent();
            assertThatThrownBy(() -> owner.queryForList(
                "select id from withdrawal_request where id = ? for update nowait", withdrawal.id()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("could not obtain lock");
            return null;
        });
    }

    @Test
    void givenAResolvedRequest_whenResolvedAgain_thenNothingChangesAndItFails() {
        NewWithdrawal withdrawal = new NewWithdrawal(UUID.randomUUID(), placed(OrderStatus.PAID).id(),
            new WithdrawalCode("ARR-7K2M9QXZ"), false, NOW);
        requested(withdrawal);
        asAdmin.resolve(withdrawal.id(), NOW.plusSeconds(60));

        assertThatThrownBy(() -> asAdmin.resolve(withdrawal.id(), NOW.plusSeconds(120)))
            .hasMessageContaining("is not a locked received one");
        assertThat(asAdmin.lockForResolution(withdrawal.id())).map(LockedWithdrawal::resolvedAt)
            .contains(Optional.of(NOW.plusSeconds(60)));
    }

    @Test
    void givenAGuestACustomerOrTheSystem_whenResolvingARequest_thenRowLevelSecurityHidesIt() {
        NewWithdrawal withdrawal = new NewWithdrawal(UUID.randomUUID(), placed(OrderStatus.PAID).id(),
            new WithdrawalCode("ARR-7K2M9QXZ"), false, NOW);
        requested(withdrawal);
        Actor guest = Actor.anonymous(Optional.empty(), Optional.empty());
        Actor customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        for (Actor actor : List.of(guest, customer)) {
            assertThat(asAdmin.as(actor, () -> asAdmin.adapter().page(Optional.empty(), 0, 10)))
                .as("listed by %s", actor).isEmpty();
        }
        // The system reads the requests, to compose their emails, but never resolves them.
        for (Actor actor : List.of(guest, customer, Actor.system())) {
            assertThat(asAdmin.as(actor, () -> asAdmin.adapter().lockForResolution(withdrawal.id())))
                .as("locked by %s", actor).isEmpty();
            assertThatThrownBy(() -> asAdmin.as(actor, () -> {
                asAdmin.adapter().resolve(withdrawal.id(), NOW.plusSeconds(60));
                return null;
            })).as("resolved by %s", actor).hasMessageContaining("is not a locked received one");
        }
        assertThat(owner.queryForObject("select status from withdrawal_request where id = ?", String.class,
            withdrawal.id())).isEqualTo(WithdrawalStatus.RECEIVED.name());
    }

    /** Every call as the administrator in its own committed transaction. */
    record AsAdmin(JdbcWithdrawalStore adapter, TransactionTemplate transactions)
        implements WithdrawalPages, ActiveWithdrawalViews, WithdrawalResolutions {

        <T> T as(Actor actor, Supplier<T> block) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> block.get()));
        }

        <T> T inOneTransaction(Supplier<T> block) {
            return as(ADMIN, block);
        }

        @Override
        public List<WithdrawalSummary> page(Optional<WithdrawalStatus> status, int offset, int limit) {
            return inOneTransaction(() -> adapter.page(status, offset, limit));
        }

        @Override
        public Optional<ActiveWithdrawal> activeViewOf(OrderId order) {
            return inOneTransaction(() -> adapter.activeViewOf(order));
        }

        @Override
        public Optional<LockedWithdrawal> lockForResolution(UUID request) {
            return inOneTransaction(() -> adapter.lockForResolution(request));
        }

        @Override
        public void resolve(UUID request, Instant at) {
            inOneTransaction(() -> {
                adapter.resolve(request, at);
                return null;
            });
        }
    }
}
