package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EnqueuePasswordReset.Command;
import com.pijamaparty.store.notifications.application.EnqueuePasswordReset.Result;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-19 (T-E4-03): a customer asked to recover its password. */
class EnqueuePasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final EnqueuePasswordResetService service =
        new EnqueuePasswordResetService(outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID customer = UUID.randomUUID();

    @Test
    void givenACustomer_whenItAsksToRecoverItsPassword_thenItsResetEmailIsDueAtOnce() {
        assertThat(service.execute(new Command(customer))).isEqualTo(new Result.Enqueued(1));

        assertThat(outbox.versions(EmailType.PASSWORD_RESET, customer)).singleElement().satisfies(email -> {
            assertThat(email.status()).isEqualTo("PENDING");
            assertThat(email.nextAttemptAt()).isEqualTo(NOW);
        });
    }

    @Test
    void givenAResetEmailNotSentYet_whenAnotherIsAskedFor_thenTheWaitingOneTakesIt() {
        service.execute(new Command(customer));

        assertThat(service.execute(new Command(customer))).isEqualTo(new Result.PendingTakesIt());
        assertThat(outbox.versions(EmailType.PASSWORD_RESET, customer)).hasSize(1);
    }

    @Test
    void givenTheResetEmailWasSent_whenAnotherIsAskedFor_thenANewVersionIsDue() {
        service.execute(new Command(customer));
        outbox.sent(outbox.lockNextDue(NOW).orElseThrow().id(), "re_sent", NOW);
        var later = new EnqueuePasswordResetService(outbox,
            Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC));

        assertThat(later.execute(new Command(customer))).isEqualTo(new Result.Enqueued(2));
        assertThat(outbox.versions(EmailType.PASSWORD_RESET, customer)).hasSize(2);
    }
}
