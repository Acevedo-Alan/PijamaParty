package com.pijamaparty.store.platform.testing;

import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs SQL as a given actor in a real, committed transaction through the application's transaction manager, so the
 * Row Level Security context is set exactly as in production (RNF-27). Every policy is tested with the five actors
 * below: customer A, customer B, a guest holding a token, the administrator and the system.
 *
 * <pre>{@code
 * var rls = new RlsPolicyTester(transactionManager, jdbc);
 * assertThat(rls.as(RlsPolicyTester.CUSTOMER_B, sql -> sql.queryForList("select id from orders", UUID.class)))
 *     .doesNotContain(orderOfCustomerA);
 * }</pre>
 */
public final class RlsPolicyTester {

    public static final UserId CUSTOMER_A_ID = UserId.of(new UUID(0xA, 1));
    public static final UserId CUSTOMER_B_ID = UserId.of(new UUID(0xB, 1));
    public static final UserId ADMIN_ID = UserId.of(new UUID(0xAD, 1));
    public static final CartToken GUEST_CART = new CartToken("guest-cart-token-for-tests");
    public static final TrackingToken GUEST_TRACKING = new TrackingToken("guest-tracking-token-for-tests");

    public static final Actor CUSTOMER_A = Actor.customer(CUSTOMER_A_ID, Optional.empty(), Optional.empty());
    public static final Actor CUSTOMER_B = Actor.customer(CUSTOMER_B_ID, Optional.empty(), Optional.empty());
    public static final Actor GUEST = Actor.anonymous(Optional.of(GUEST_CART), Optional.of(GUEST_TRACKING));
    public static final Actor ADMIN = Actor.admin(ADMIN_ID);
    public static final Actor SYSTEM = Actor.system();

    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;

    public RlsPolicyTester(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jdbc = jdbc;
    }

    public <T> T as(Actor actor, Function<JdbcTemplate, T> work) {
        return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.apply(jdbc)));
    }

    /** A transaction with no actor bound: the default-closed case. */
    public <T> T withoutContext(Function<JdbcTemplate, T> work) {
        return transactions.execute(status -> work.apply(jdbc));
    }
}
