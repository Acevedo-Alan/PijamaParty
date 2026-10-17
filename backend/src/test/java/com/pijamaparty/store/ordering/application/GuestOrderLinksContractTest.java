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
 * Contract of {@link GuestOrderLinks} (UC-22, T-E4-08): the guest orders of a verified email join that customer, and
 * no other order is touched. Read back through {@link CustomerOrderPages}.
 */
public abstract class GuestOrderLinksContractTest<
    T extends SaveOrders & OrderNumbers & GuestOrderLinks & CustomerOrderPages> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-17T15:00:00.123456Z");
    protected static final String ANA = "ana@example.com";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<OrderLine> LINES = List.of(
        new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 1));

    private static final ShippingAddress ADDRESS = new ShippingAddress("San Martín", "1234", Optional.empty(),
        Optional.empty(), "Santa Fe", "3000");

    /** A customer whose email, already verified, is {@code email}. */
    protected abstract UserId verifiedCustomer(String email);

    /** An account deletion (UC-28) unlinked this guest order. */
    protected abstract void unlinked(OrderId order);

    /** The buyer corrected the email of this order (UC-17). */
    protected abstract void emailCorrected(OrderId order, String email);

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    @Test
    void givenGuestOrdersWithTheCustomersEmail_whenLinked_thenTheyJoinItsOrders() {
        UserId ana = verifiedCustomer(ANA);
        NewOrder first = placed(Optional.empty(), ANA);
        NewOrder second = placed(Optional.empty(), ANA);
        persistAndForget();

        assertThat(subject.link(ana, ANA, NOW)).isEqualTo(2);
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).extracting(CustomerOrderSummary::id)
            .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void givenGuestOrdersOfOtherEmailsAndAnotherCustomersOrder_whenLinked_thenNoneOfThemJoins() {
        UserId ana = verifiedCustomer(ANA);
        UserId bruno = verifiedCustomer("bruno@example.com");
        placed(Optional.empty(), "bruno@example.com");
        NewOrder brunosWithAnasEmail = placed(Optional.of(bruno), ANA);
        persistAndForget();

        assertThat(subject.link(ana, ANA, NOW)).isZero();
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).isEmpty();
        assertThat(subject.page(bruno, 0, 20)).extracting(CustomerOrderSummary::id)
            .containsExactly(brunosWithAnasEmail.id());
    }

    @Test
    void givenGuestOrdersWhoseEmailWasCorrected_whenLinked_thenTheEmailAsLastCorrectedDecides() {
        UserId ana = verifiedCustomer(ANA);
        NewOrder correctedToAnas = placed(Optional.empty(), "ana@exmaple.com");
        emailCorrected(correctedToAnas.id(), ANA);
        NewOrder correctedAway = placed(Optional.empty(), ANA);
        emailCorrected(correctedAway.id(), "otra@example.com");
        persistAndForget();

        assertThat(subject.link(ana, ANA, NOW)).isOne();
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).extracting(CustomerOrderSummary::id)
            .containsExactly(correctedToAnas.id());
    }

    @Test
    void givenAGuestOrderUnlinkedByAnAccountDeletion_whenLinked_thenItNeverJoinsAgain() {
        UserId ana = verifiedCustomer(ANA);
        NewOrder unlinkedOrder = placed(Optional.empty(), ANA);
        unlinked(unlinkedOrder.id());
        persistAndForget();

        assertThat(subject.link(ana, ANA, NOW)).isZero();
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).isEmpty();
    }

    @Test
    void givenGuestOrdersAlreadyLinked_whenLinkedAgain_thenNothingMoreJoinsButANewGuestOrderDoes() {
        UserId ana = verifiedCustomer(ANA);
        placed(Optional.empty(), ANA);
        subject.link(ana, ANA, NOW);
        persistAndForget();

        assertThat(subject.link(ana, ANA, NOW.plusSeconds(60))).isZero();
        NewOrder later = placed(Optional.empty(), ANA);
        persistAndForget();

        assertThat(subject.link(ana, ANA, NOW.plusSeconds(120))).isOne();
        persistAndForget();
        assertThat(subject.page(ana, 0, 20)).extracting(CustomerOrderSummary::id).contains(later.id()).hasSize(2);
    }

    protected NewOrder placed(Optional<UserId> customer, String email) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), token.hash(), customer,
            new BuyerDetails("Ana María Pérez", email, "3425551234", "12345678"), ADDRESS, Province.SANTA_FE, LINES,
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", NOW, StockHold.expiryOf(NOW));
        subject.insert(order);
        return order;
    }
}
