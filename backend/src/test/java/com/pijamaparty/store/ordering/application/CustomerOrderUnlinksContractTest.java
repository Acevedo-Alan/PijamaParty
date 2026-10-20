package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.api.Province;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerOrderUnlinks} (UC-28, T-E4-16): every order of a deleted customer loses its account, and
 * never joins one again, not even a new account with the same email. No other order is touched. Read back through
 * {@link CustomerOrderPages}; joined again through {@link GuestOrderLinks}.
 */
public abstract class CustomerOrderUnlinksContractTest<
    T extends SaveOrders & OrderNumbers & CustomerOrderUnlinks & CustomerOrderPages & GuestOrderLinks>
    extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-17T15:00:00.123456Z");
    protected static final String ANA = "ana@example.com";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A customer whose email, already verified, is {@code email}; the email of a deleted account can be reused. */
    protected abstract UserId verifiedCustomer(String email);

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    @Test
    void givenACustomersOrders_whenUnlinked_thenItHasNoneLeftAndAnotherCustomerKeepsItsOwn() {
        UserId ana = verifiedCustomer(ANA);
        UserId bruno = verifiedCustomer("bruno@example.com");
        placed(Optional.of(ana), ANA);
        placed(Optional.of(ana), ANA);
        NewOrder brunos = placed(Optional.of(bruno), "bruno@example.com");
        persistAndForget();

        assertThat(subject.unlinkAll(ana, NOW)).isEqualTo(2);
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).isEmpty();
        assertThat(subject.page(bruno, 0, 20)).extracting(CustomerOrderSummary::id).containsExactly(brunos.id());
    }

    @Test
    void givenOrdersAlreadyUnlinked_whenUnlinkedAgain_thenNothingMoreIsUnlinked() {
        UserId ana = verifiedCustomer(ANA);
        placed(Optional.of(ana), ANA);
        subject.unlinkAll(ana, NOW);
        persistAndForget();

        assertThat(subject.unlinkAll(ana, NOW.plusSeconds(60))).isZero();
    }

    @Test
    void givenUnlinkedOrders_whenANewAccountWithTheSameEmailSignsIn_thenTheyNeverJoinIt() {
        UserId ana = verifiedCustomer(ANA);
        placed(Optional.of(ana), ANA);
        subject.unlinkAll(ana, NOW);
        persistAndForget();

        UserId anaAgain = verifiedCustomer(ANA);
        assertThat(subject.link(anaAgain, ANA, NOW.plusSeconds(60))).isZero();
        persistAndForget();

        assertThat(subject.page(anaAgain, 0, 20)).isEmpty();
    }

    protected NewOrder placed(Optional<UserId> customer, String email) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), token.hash(), customer,
            new BuyerDetails("Ana María", "Pérez", email, "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "3000"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 1)),
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", NOW, StockHold.expiryOf(NOW));
        subject.insert(order);
        return order;
    }
}
