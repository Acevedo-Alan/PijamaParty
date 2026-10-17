package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EnqueueLinkConfirmation.Command;
import com.pijamaparty.store.notifications.application.EnqueueLinkConfirmation.Result;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-14): a customer needs the email that confirms linking an external account. */
class EnqueueLinkConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final EnqueueLinkConfirmationService service =
        new EnqueueLinkConfirmationService(outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID customer = UUID.randomUUID();

    @Test
    void givenAPendingLink_whenRequested_thenItsLinkConfirmationEmailIsDueAtOnce() {
        assertThat(service.execute(new Command(customer))).isEqualTo(new Result.Enqueued(1));

        assertThat(outbox.versions(EmailType.LINK_CONFIRMATION, customer)).singleElement().satisfies(email -> {
            assertThat(email.status()).isEqualTo("PENDING");
            assertThat(email.nextAttemptAt()).isEqualTo(NOW);
        });
    }

    @Test
    void givenALinkConfirmationEmailNotSentYet_whenAnotherIsAskedFor_thenTheWaitingOneTakesIt() {
        service.execute(new Command(customer));

        assertThat(service.execute(new Command(customer))).isEqualTo(new Result.PendingTakesIt());
        assertThat(outbox.versions(EmailType.LINK_CONFIRMATION, customer)).hasSize(1);
    }

    @Test
    void givenTheLinkConfirmationEmailWasSent_whenAnotherIsAskedFor_thenANewVersionIsDue() {
        service.execute(new Command(customer));
        outbox.sent(outbox.lockNextDue(NOW).orElseThrow().id(), "re_sent", NOW);
        var later = new EnqueueLinkConfirmationService(outbox,
            Clock.fixed(NOW.plus(Duration.ofDays(2)), ZoneOffset.UTC));

        assertThat(later.execute(new Command(customer))).isEqualTo(new Result.Enqueued(2));
        assertThat(outbox.versions(EmailType.LINK_CONFIRMATION, customer)).hasSize(2);
    }
}
