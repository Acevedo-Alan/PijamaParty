package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.RecordEmailEvent.Command;
import com.pijamaparty.store.notifications.application.RecordEmailEvent.Result;
import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** UC-17, UC-18 (T-E3-22): a bounce or a complaint the provider notifies, with in-memory fakes. */
class RecordEmailEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");
    private static final String MESSAGE_ID = "4ef9a417-02e9-4d39-ad75-9611e0fcc33c";

    private final InMemoryEmailEvents events = new InMemoryEmailEvents();
    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final RecordEmailEventService service =
        new RecordEmailEventService(events, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID order = UUID.randomUUID();

    /** The buyer's email of the order, sent with {@link #MESSAGE_ID}. */
    @BeforeEach
    void sendTheBuyersEmail() {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW.minusSeconds(120));
        outbox.sent(outbox.lockNextDue(NOW).orElseThrow().id(), MESSAGE_ID, NOW.minusSeconds(60));
    }

    @ParameterizedTest
    @EnumSource(value = EmailEventType.class, names = {"PERMANENT_BOUNCE", "SUPPRESSED"})
    void givenTheBuyersEmailNeverArrived_whenNotified_thenItIsBouncedAndTheEventRecorded(EmailEventType type) {
        Result result = service.execute(command("msg_1", type));

        assertThat(result).isEqualTo(new Result.Bounced(EmailType.ORDER_PAID_CUSTOMER, order));
        assertThat(outbox.statusOf(EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.BOUNCED);
        assertThat(events.recorded())
            .containsExactly(new InMemoryEmailEvents.Recorded(MailProvider.RESEND, "msg_1", type, MESSAGE_ID));
    }

    @ParameterizedTest
    @EnumSource(value = EmailEventType.class, names = {"TRANSIENT_BOUNCE", "COMPLAINT"})
    void givenTheBuyersEmailArrivedOrMayStill_whenNotified_thenOnlyTheEventIsRecorded(EmailEventType type) {
        Result result = service.execute(command("msg_1", type));

        assertThat(result).isEqualTo(new Result.Recorded(type));
        assertThat(outbox.statusOf(EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.SENT);
        assertThat(events.recorded()).hasSize(1);
    }

    @Test
    void givenABounceAlreadyProcessed_whenItsRetryArrives_thenNothingIsDoneAgain() {
        service.execute(command("msg_1", EmailEventType.PERMANENT_BOUNCE));

        Result result = service.execute(command("msg_1", EmailEventType.PERMANENT_BOUNCE));

        assertThat(result).isEqualTo(new Result.AlreadyProcessed());
        assertThat(events.recorded()).hasSize(1);
    }

    @Test
    void givenABounceOfAnEmailThisStoreDidNotSend_whenNotified_thenItIsRecordedAndUnknown() {
        Result result = service.execute(new Command(MailProvider.RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE,
            "another-email"));

        assertThat(result).isEqualTo(new Result.UnknownEmail());
        assertThat(outbox.statusOf(EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.SENT);
        assertThat(events.recorded()).hasSize(1);
    }

    @Test
    void givenAnEmailAlreadyBounced_whenAnotherEventSaysItBounced_thenItIsRecordedAndUnknown() {
        service.execute(command("msg_1", EmailEventType.SUPPRESSED));

        Result result = service.execute(command("msg_2", EmailEventType.PERMANENT_BOUNCE));

        assertThat(result).isEqualTo(new Result.UnknownEmail());
        assertThat(events.recorded()).hasSize(2);
    }

    @Test
    void givenTheAdministratorsNoticeBounced_whenNotified_thenItIsBouncedWithItsType() {
        outbox.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW.minusSeconds(120));
        outbox.sent(outbox.lockNextDue(NOW).orElseThrow().id(), "admin-email", NOW.minusSeconds(60));

        Result result = service.execute(new Command(MailProvider.RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE,
            "admin-email"));

        assertThat(result).isEqualTo(new Result.Bounced(EmailType.ORDER_PAID_ADMIN, order));
        assertThat(outbox.statusOf(EmailType.ORDER_PAID_CUSTOMER, order)).contains(EmailStatus.SENT);
    }

    private static Command command(String eventId, EmailEventType type) {
        return new Command(MailProvider.RESEND, eventId, type, MESSAGE_ID);
    }
}
