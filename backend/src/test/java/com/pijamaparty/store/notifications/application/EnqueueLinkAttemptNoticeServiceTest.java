package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EnqueueLinkAttemptNotice.Command;
import com.pijamaparty.store.notifications.application.EnqueueLinkAttemptNotice.Result;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-14): someone typed a customer's email to connect an external account, and the customer is told. */
class EnqueueLinkAttemptNoticeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final EnqueueLinkAttemptNoticeService service =
        new EnqueueLinkAttemptNoticeService(outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID customer = UUID.randomUUID();

    @Test
    void givenAnAttempt_whenRequested_thenTheNoticeIsDueAtOnce() {
        assertThat(service.execute(new Command(customer))).isEqualTo(new Result.Enqueued(1));

        assertThat(outbox.versions(EmailType.LINK_ATTEMPT_NOTICE, customer)).singleElement().satisfies(email -> {
            assertThat(email.status()).isEqualTo("PENDING");
            assertThat(email.nextAttemptAt()).isEqualTo(NOW);
        });
    }

    @Test
    void givenANoticeNotSentYet_whenAnotherAttemptComes_thenTheWaitingOneTakesIt() {
        service.execute(new Command(customer));

        assertThat(service.execute(new Command(customer))).isEqualTo(new Result.PendingTakesIt());
        assertThat(outbox.versions(EmailType.LINK_ATTEMPT_NOTICE, customer)).hasSize(1);
    }

    @Test
    void givenTheNoticeWasSent_whenAnotherAttemptComes_thenANewVersionIsDue() {
        service.execute(new Command(customer));
        outbox.sent(outbox.lockNextDue(NOW).orElseThrow().id(), "re_sent", NOW);
        var later = new EnqueueLinkAttemptNoticeService(outbox,
            Clock.fixed(NOW.plus(Duration.ofDays(1)), ZoneOffset.UTC));

        assertThat(later.execute(new Command(customer))).isEqualTo(new Result.Enqueued(2));
        assertThat(outbox.versions(EmailType.LINK_ATTEMPT_NOTICE, customer)).hasSize(2);
    }
}
