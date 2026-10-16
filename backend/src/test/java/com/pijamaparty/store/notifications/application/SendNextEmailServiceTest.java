package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.notifications.application.InMemoryEmailSender.Mode;
import com.pijamaparty.store.notifications.application.SendNextEmail.Result;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SendNextEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:30Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final InMemoryEmailComposer buyer = new InMemoryEmailComposer(EmailType.ORDER_PAID_CUSTOMER);
    private final InMemoryEmailComposer admin = new InMemoryEmailComposer(EmailType.ORDER_PAID_ADMIN);
    private final InMemoryEmailComposer withdrawalBuyer =
        new InMemoryEmailComposer(EmailType.WITHDRAWAL_RECEIVED_CUSTOMER);
    private final InMemoryEmailComposer withdrawalAdmin = new InMemoryEmailComposer(EmailType.WITHDRAWAL_RECEIVED_ADMIN);
    private final InMemoryEmailComposer emailVerification = new InMemoryEmailComposer(EmailType.EMAIL_VERIFICATION);
    private final InMemoryEmailSender sender = new InMemoryEmailSender();
    private final ClaimNextEmailService claims = new ClaimNextEmailService(outbox, outbox, clock);
    private final SendNextEmailService service =
        new SendNextEmailService(claims, outbox,
            List.of(buyer, admin, withdrawalBuyer, withdrawalAdmin, emailVerification), sender, clock);
    private final UUID order = UUID.randomUUID();

    @Test
    void givenNothingInTheOutbox_whenTheNextEmailIsSent_thenNoneIsDue() {
        assertThat(service.execute()).isEqualTo(new Result.NoneDue());
        assertThat(sender.attempted()).isEmpty();
    }

    @Test
    void givenAnEmailDue_whenSent_thenTheServiceGetsItsComposedMessageAndItIsSent() {
        EmailMessage message = buyer.writes(order);
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);

        Result result = service.execute();

        assertThat(result).isEqualTo(new Result.Sent(EmailType.ORDER_PAID_CUSTOMER, order));
        assertThat(sender.delivered()).containsExactly(message);
        assertThat(outbox.find(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email -> {
            assertThat(email.status()).isEqualTo("SENT");
            assertThat(email.providerMessageId()).contains("email-1");
            assertThat(email.sentAt()).contains(NOW);
        });
        assertThat(service.execute()).isEqualTo(new Result.NoneDue());
    }

    @Test
    void givenTheBuyersAndTheAdministratorsEmails_whenSentUntilNoneIsDue_thenEachIsSentOnce() {
        buyer.writes(order);
        admin.writes(order);
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        outbox.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);

        assertThat(service.execute()).isInstanceOf(Result.Sent.class);
        assertThat(service.execute()).isInstanceOf(Result.Sent.class);
        assertThat(service.execute()).isEqualTo(new Result.NoneDue());

        assertThat(sender.delivered()).extracting(EmailMessage::to)
            .containsExactlyInAnyOrder("order_paid_customer@example.com", "order_paid_admin@example.com");
    }

    @Test
    void givenTheServiceDown_whenSent_thenTheEmailIsRetriedAMinuteLaterAndThenSent() {
        buyer.writes(order);
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        sender.mode(Mode.DOWN);

        assertThat(service.execute()).isEqualTo(new Result.Retrying(EmailType.ORDER_PAID_CUSTOMER, order, 1));
        assertThat(service.execute()).isEqualTo(new Result.NoneDue());

        sender.mode(Mode.ACCEPT);
        now.set(NOW.plus(Duration.ofMinutes(1)));

        assertThat(service.execute()).isEqualTo(new Result.Sent(EmailType.ORDER_PAID_CUSTOMER, order));
        assertThat(sender.delivered()).hasSize(1);
        assertThat(outbox.find(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email -> {
            assertThat(email.status()).isEqualTo("SENT");
            assertThat(email.attempts()).isEqualTo(2);
        });
    }

    @Test
    void givenTheServiceDownForFiveAttempts_whenSentAtEachRetry_thenTheFifthFails() {
        buyer.writes(order);
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        sender.mode(Mode.DOWN);
        Instant at = NOW;
        for (int attempt = 1; attempt <= 4; attempt++) {
            now.set(at);
            assertThat(service.execute()).isEqualTo(new Result.Retrying(EmailType.ORDER_PAID_CUSTOMER, order,
                attempt));
            at = outbox.find(EmailType.ORDER_PAID_CUSTOMER, order).orElseThrow().nextAttemptAt();
        }
        now.set(at);

        assertThat(service.execute()).isEqualTo(new Result.Failed(EmailType.ORDER_PAID_CUSTOMER, order));
        assertThat(sender.attempted()).hasSize(5);
        assertThat(outbox.find(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email -> {
            assertThat(email.status()).isEqualTo("FAILED");
            assertThat(email.attempts()).isEqualTo(5);
        });
        now.set(at.plus(Duration.ofDays(1)));
        assertThat(service.execute()).isEqualTo(new Result.NoneDue());
    }

    @Test
    void givenTheServiceRejectsTheEmail_whenSent_thenItFailsAtOnce() {
        buyer.writes(order);
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        sender.mode(Mode.REJECT);

        assertThat(service.execute()).isEqualTo(new Result.Failed(EmailType.ORDER_PAID_CUSTOMER, order));
        assertThat(outbox.find(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email ->
            assertThat(email.status()).isEqualTo("FAILED"));
        now.set(NOW.plus(Duration.ofHours(2)));
        assertThat(service.execute()).isEqualTo(new Result.NoneDue());
    }

    @Test
    void givenAnEmailWhoseOrderIsGone_whenSent_thenItFailsWithoutCallingTheService() {
        outbox.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);

        assertThat(service.execute()).isEqualTo(new Result.Failed(EmailType.ORDER_PAID_ADMIN, order));
        assertThat(sender.attempted()).isEmpty();
        assertThat(outbox.find(EmailType.ORDER_PAID_ADMIN, order)).hasValueSatisfying(email ->
            assertThat(email.status()).isEqualTo("FAILED"));
    }

    @Test
    void givenAnEmailWhoseAttemptsWereSpentByAStoppedProcess_whenDue_thenItFailsWithoutCallingTheService() {
        buyer.writes(order);
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        Instant at = NOW;
        for (int attempt = 1; attempt <= 5; attempt++) {
            now.set(at);
            claims.execute();
            at = outbox.find(EmailType.ORDER_PAID_CUSTOMER, order).orElseThrow().nextAttemptAt();
        }
        now.set(at);

        assertThat(service.execute()).isEqualTo(new Result.Failed(EmailType.ORDER_PAID_CUSTOMER, order));
        assertThat(sender.attempted()).isEmpty();
    }

    @Test
    void givenAComposerMissingOrRepeated_whenTheServiceIsCreated_thenItFails() {
        assertThatThrownBy(() -> new SendNextEmailService(claims, outbox, List.of(buyer), sender, clock))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendNextEmailService(claims, outbox,
            List.of(buyer, admin, withdrawalBuyer, withdrawalAdmin, buyer), sender,
            clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
