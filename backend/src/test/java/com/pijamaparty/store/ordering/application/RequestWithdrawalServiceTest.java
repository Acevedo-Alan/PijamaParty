package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.api.WithdrawalRequested;
import com.pijamaparty.store.ordering.application.InMemoryWithdrawalStore.Stored;
import com.pijamaparty.store.ordering.application.RequestWithdrawal.Command;
import com.pijamaparty.store.ordering.application.RequestWithdrawal.Result;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-29 (T-E3-25): a withdrawal requested with the order number and the purchase email. */
class RequestWithdrawalServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-01T13:00:00Z");
    // 2026-10-05 in Argentina.
    private static final Instant DELIVERED_AT = Instant.parse("2026-10-05T18:00:00Z");
    // 2026-10-15, the 10th day after the delivery, in Argentina.
    private static final Instant LAST_DAY = Instant.parse("2026-10-15T20:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("0192f0aa-0000-7000-8000-000000000001");
    private static final WithdrawalCode CODE = new WithdrawalCode("ARR-7K2M9QXZ");

    private final InMemoryWithdrawalStore store = new InMemoryWithdrawalStore();
    private final InMemoryOrderEvents events = new InMemoryOrderEvents();
    private final Deque<WithdrawalCode> codes = new ArrayDeque<>(List.of(CODE));

    private RequestWithdrawalService serviceAt(Instant now) {
        return new RequestWithdrawalService(store, store, store, codes::pop, events, () -> REQUEST_ID,
            Clock.fixed(now, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING", "SHIPPED"})
    void givenAPaidOrder_whenTheFormIsSentWithItsNumberAndEmail_thenARequestInTermIsRecordedWithACode(
        OrderStatus status) {
        WithdrawableOrder order = store.place(status, PLACED_AT, Optional.empty());

        assertThat(serviceAt(LAST_DAY.plusSeconds(86_400 * 30)).execute(
            new Command(Long.toString(order.number()), "ana@example.com")))
            .isEqualTo(new Result.Requested(CODE));

        assertThat(store.requests()).containsExactly(new Stored(new NewWithdrawal(REQUEST_ID, order.id(), CODE, false,
            LAST_DAY.plusSeconds(86_400 * 30)), Optional.empty()));
        assertThat(events.published()).containsExactly(
            new WithdrawalRequested(REQUEST_ID, order.id().value(), order.number()));
    }

    @Test
    void givenTheNumberWithItsHashAndTheEmailTypedWithCapitalsAndSpaces_whenSent_thenTheOrderStillMatches() {
        WithdrawableOrder order = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());

        assertThat(serviceAt(LAST_DAY).execute(new Command(" #" + order.number() + " ", "  Ana@Example.COM ")))
            .isEqualTo(new Result.Requested(CODE));
    }

    @Test
    void givenAnOrderDeliveredOnTheLastDayOfTheTerm_whenTheFormIsSent_thenItIsInTerm() {
        WithdrawableOrder order = store.place(OrderStatus.DELIVERED, PLACED_AT, Optional.of(DELIVERED_AT));

        serviceAt(LAST_DAY).execute(new Command(Long.toString(order.number()), "ana@example.com"));

        assertThat(store.requests()).singleElement().extracting(stored -> stored.withdrawal().outOfTerm())
            .isEqualTo(false);
    }

    @Test
    void givenAnOrderDeliveredMoreThan10DaysAgo_whenTheFormIsSent_thenTheRequestIsRecordedFlaggedAsOutOfTerm() {
        WithdrawableOrder order = store.place(OrderStatus.DELIVERED, PLACED_AT, Optional.of(DELIVERED_AT));

        assertThat(serviceAt(LAST_DAY.plusSeconds(86_400)).execute(
            new Command(Long.toString(order.number()), "ana@example.com")))
            .isEqualTo(new Result.Requested(CODE));

        assertThat(store.requests()).singleElement().extracting(stored -> stored.withdrawal().outOfTerm())
            .isEqualTo(true);
        assertThat(events.published()).hasSize(1);
    }

    @Test
    void givenAnOrderNumberWithAnEmailThatDoesNotMatch_whenTheFormIsSent_thenAGenericAnswerAndNothingIsRecorded() {
        WithdrawableOrder order = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());

        assertThat(serviceAt(LAST_DAY).execute(new Command(Long.toString(order.number()), "otra@example.com")))
            .isEqualTo(new Result.NoMatchingOrder());

        assertThat(store.requests()).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAnUnknownOrderNumber_whenTheFormIsSent_thenTheSameGenericAnswer() {
        store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());

        assertThat(serviceAt(LAST_DAY).execute(new Command("999999", "ana@example.com")))
            .isEqualTo(new Result.NoMatchingOrder());
        assertThat(store.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "#", "0", "10 01"})
    void givenTextThatIsNoOrderNumber_whenTheFormIsSent_thenTheSameGenericAnswer(String orderNumber) {
        store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());

        assertThat(serviceAt(LAST_DAY).execute(new Command(orderNumber, "ana@example.com")))
            .isEqualTo(new Result.NoMatchingOrder());
    }

    @Test
    void givenAnEmailThatBreaksTheRule_whenTheFormIsSent_thenTheSameGenericAnswer() {
        WithdrawableOrder order = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());

        assertThat(serviceAt(LAST_DAY).execute(new Command(Long.toString(order.number()), "ana example.com")))
            .isEqualTo(new Result.NoMatchingOrder());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "EXPIRED", "CANCELLED"})
    void givenAnOrderWithoutAPaymentToReturn_whenTheFormIsSent_thenItIsRefusedAndNothingIsRecorded(
        OrderStatus status) {
        WithdrawableOrder order = store.place(status, PLACED_AT, Optional.empty());

        assertThat(serviceAt(LAST_DAY).execute(new Command(Long.toString(order.number()), "ana@example.com")))
            .isEqualTo(new Result.NotWithdrawable(status));

        assertThat(store.requests()).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAnOrderWithAnActiveRequest_whenAnotherIsSent_thenTheSameCodeAndNoSecondRequestNorEmail() {
        WithdrawableOrder order = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());
        var command = new Command(Long.toString(order.number()), "ana@example.com");
        serviceAt(LAST_DAY).execute(command);

        assertThat(serviceAt(LAST_DAY.plusSeconds(60)).execute(command)).isEqualTo(new Result.AlreadyRequested(CODE));

        assertThat(store.requests()).hasSize(1);
        assertThat(events.published()).hasSize(1);
    }

    @Test
    void givenAnOrderWhoseRequestWasResolved_whenAnotherIsSent_thenANewRequestIsRecorded() {
        WithdrawableOrder order = store.place(OrderStatus.DELIVERED, PLACED_AT, Optional.of(DELIVERED_AT));
        store.insert(new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode("ARR-22222222"), false,
            DELIVERED_AT));
        store.resolve(store.requests().getFirst().withdrawal().id(), LAST_DAY);

        assertThat(serviceAt(LAST_DAY).execute(new Command(Long.toString(order.number()), "ana@example.com")))
            .isEqualTo(new Result.Requested(CODE));
        assertThat(store.requests()).hasSize(2);
    }

    @Test
    void givenACodeAlreadyTaken_whenTheFormIsSent_thenAnotherCodeIsDrawn() {
        WithdrawableOrder other = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());
        store.insert(new NewWithdrawal(UUID.randomUUID(), other.id(), CODE, false, PLACED_AT));
        WithdrawableOrder order = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());
        codes.addLast(new WithdrawalCode("ARR-33333333"));

        assertThat(serviceAt(LAST_DAY).execute(new Command(Long.toString(order.number()), "ana@example.com")))
            .isEqualTo(new Result.Requested(new WithdrawalCode("ARR-33333333")));
    }

    @Test
    void givenEveryCodeDrawnAlreadyTaken_whenTheFormIsSent_thenItFailsAfterFiveAttemptsWithoutRecording() {
        WithdrawableOrder other = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());
        store.insert(new NewWithdrawal(UUID.randomUUID(), other.id(), CODE, false, PLACED_AT));
        WithdrawableOrder order = store.place(OrderStatus.PAID, PLACED_AT, Optional.empty());
        RequestWithdrawalService service = new RequestWithdrawalService(store, store, store, () -> CODE, events,
            () -> REQUEST_ID, Clock.fixed(LAST_DAY, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.execute(new Command(Long.toString(order.number()), "ana@example.com")))
            .isInstanceOf(IllegalStateException.class);
        assertThat(store.requests()).hasSize(1);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenACommand_whenPrinted_thenTheEmailIsHidden() {
        assertThat(new Command("1001", "ana@example.com").toString()).doesNotContain("ana@example.com");
    }
}
