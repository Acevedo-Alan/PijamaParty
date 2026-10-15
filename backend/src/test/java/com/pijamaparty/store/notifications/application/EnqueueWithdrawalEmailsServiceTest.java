package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EnqueueWithdrawalEmails.Command;
import com.pijamaparty.store.notifications.application.EnqueueWithdrawalEmails.Result;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-29 (T-E3-25): the emails of a withdrawal request. */
class EnqueueWithdrawalEmailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final EnqueueWithdrawalEmailsService service =
        new EnqueueWithdrawalEmailsService(outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID request = UUID.randomUUID();

    @Test
    void givenAWithdrawalRequest_whenItsEmailsAreEnqueued_thenTheCodeToTheBuyerAndTheNoticeAreDueAtOnce() {
        assertThat(service.execute(new Command(request))).isEqualTo(new Result(2));

        for (EmailType type : new EmailType[] {EmailType.WITHDRAWAL_RECEIVED_CUSTOMER,
            EmailType.WITHDRAWAL_RECEIVED_ADMIN}) {
            assertThat(outbox.find(type, request)).hasValueSatisfying(email -> {
                assertThat(email.status()).isEqualTo("PENDING");
                assertThat(email.nextAttemptAt()).isEqualTo(NOW);
            });
        }
        assertThat(outbox.size()).isEqualTo(2);
    }

    @Test
    void givenARequestWhoseEmailsWereEnqueued_whenEnqueuedAgain_thenNothingIsAdded() {
        service.execute(new Command(request));

        assertThat(service.execute(new Command(request))).isEqualTo(new Result(0));
        assertThat(outbox.size()).isEqualTo(2);
    }
}
