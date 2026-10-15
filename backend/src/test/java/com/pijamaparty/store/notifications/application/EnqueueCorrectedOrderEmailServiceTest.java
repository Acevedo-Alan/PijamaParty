package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EnqueueCorrectedOrderEmail.Command;
import com.pijamaparty.store.notifications.application.EnqueueCorrectedOrderEmail.Result;
import com.pijamaparty.store.notifications.application.InMemoryEmailOutbox.Stored;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-17 (T-E3-23): the buyer corrected the email of a paid order, so its confirmation goes again. */
class EnqueueCorrectedOrderEmailServiceTest {

    private static final Instant PAID_AT = Instant.parse("2026-10-13T15:00:00Z");
    private static final Instant NOW = PAID_AT.plus(Duration.ofMinutes(10));

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final EnqueueCorrectedOrderEmailService service =
        new EnqueueCorrectedOrderEmailService(outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID order = UUID.randomUUID();

    @Test
    void givenTheConfirmationWasSent_whenTheEmailIsCorrected_thenANewVersionIsDueAtOnce() {
        paidAndBuyersEmail("SENT");

        assertThat(service.execute(new Command(order))).isEqualTo(new Result.Enqueued(1));

        assertThat(outbox.versions(EmailType.ORDER_PAID_CUSTOMER, order)).last().satisfies(email -> {
            assertThat(email.version()).isOne();
            assertThat(email.status()).isEqualTo("PENDING");
            assertThat(email.nextAttemptAt()).isEqualTo(NOW);
        });
    }

    @Test
    void givenTheConfirmationFailedOrBounced_whenTheEmailIsCorrected_thenANewVersionIsDue() {
        UUID bounced = UUID.randomUUID();
        paidAndBuyersEmail("FAILED");
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, bounced, PAID_AT);
        outbox.sent(outbox.lockNextDue(PAID_AT).orElseThrow().id(), "re_bounced", PAID_AT);
        outbox.bounce("re_bounced", PAID_AT.plusSeconds(60));

        assertThat(service.execute(new Command(order))).isEqualTo(new Result.Enqueued(1));
        assertThat(service.execute(new Command(bounced))).isEqualTo(new Result.Enqueued(1));
    }

    @Test
    void givenTheConfirmationNotAttemptedYet_whenTheEmailIsCorrected_thenItTakesTheNewEmailAndNothingIsAdded() {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, PAID_AT);

        assertThat(service.execute(new Command(order))).isEqualTo(new Result.PendingTakesIt());

        assertThat(outbox.versions(EmailType.ORDER_PAID_CUSTOMER, order)).singleElement()
            .extracting(Stored::version).isEqualTo(0);
    }

    @Test
    void givenTheConfirmationStillRetrying_whenTheEmailIsCorrected_thenANewVersionIsDueToo() {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, PAID_AT);
        OutboxEmail first = outbox.lockNextDue(PAID_AT).orElseThrow();
        outbox.attempting(first.id(), 1, PAID_AT.plus(Duration.ofMinutes(1)), PAID_AT);

        assertThat(service.execute(new Command(order))).isEqualTo(new Result.Enqueued(1));
    }

    @Test
    void givenTwoCorrectionsAfterTheConfirmationWasSent_whenTheSecondArrives_thenTheFirstResendTakesIt() {
        paidAndBuyersEmail("SENT");
        service.execute(new Command(order));

        assertThat(service.execute(new Command(order))).isEqualTo(new Result.PendingTakesIt());
        assertThat(outbox.versions(EmailType.ORDER_PAID_CUSTOMER, order)).hasSize(2);
    }

    @Test
    void givenAPaidOrder_whenTheBuyersEmailIsCorrected_thenTheAdministratorsNoticeIsNotSentAgain() {
        outbox.enqueue(EmailType.ORDER_PAID_ADMIN, order, PAID_AT);
        outbox.sent(outbox.lockNextDue(PAID_AT).orElseThrow().id(), "re_admin", PAID_AT);
        paidAndBuyersEmail("SENT");

        service.execute(new Command(order));

        assertThat(outbox.versions(EmailType.ORDER_PAID_ADMIN, order)).hasSize(1);
    }

    /** The buyer's confirmation of the order, enqueued when it was paid, and then sent or failed. */
    private void paidAndBuyersEmail(String outcome) {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, PAID_AT);
        OutboxEmail email = outbox.lockNextDue(PAID_AT).orElseThrow();
        outbox.attempting(email.id(), 1, PAID_AT.plus(Duration.ofMinutes(1)), PAID_AT);
        if (outcome.equals("SENT")) {
            outbox.sent(email.id(), "re_" + order, PAID_AT);
        } else {
            outbox.failed(email.id(), PAID_AT);
        }
    }
}
