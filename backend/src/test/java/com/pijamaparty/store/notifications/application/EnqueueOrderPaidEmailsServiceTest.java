package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EnqueueOrderPaidEmails.Command;
import com.pijamaparty.store.notifications.application.EnqueueOrderPaidEmails.Result;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnqueueOrderPaidEmailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final EnqueueOrderPaidEmailsService service =
        new EnqueueOrderPaidEmailsService(outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID order = UUID.randomUUID();

    @Test
    void givenAPaidOrder_whenItsEmailsAreEnqueued_thenTheBuyersAndTheAdministratorsAreDueAtOnce() {
        Result result = service.execute(new Command(order));

        assertThat(result).isEqualTo(new Result(2));
        for (EmailType type : new EmailType[] {EmailType.ORDER_PAID_CUSTOMER, EmailType.ORDER_PAID_ADMIN}) {
            assertThat(outbox.find(type, order)).hasValueSatisfying(email -> {
                assertThat(email.status()).isEqualTo("PENDING");
                assertThat(email.nextAttemptAt()).isEqualTo(NOW);
            });
        }
    }

    @Test
    void givenAnOrderWhoseEmailsWereEnqueued_whenEnqueuedAgain_thenNothingIsAdded() {
        service.execute(new Command(order));

        assertThat(service.execute(new Command(order))).isEqualTo(new Result(0));
        assertThat(outbox.size()).isEqualTo(2);
    }

    @Test
    void givenTwoPaidOrders_whenEnqueued_thenEachGetsItsOwnEmails() {
        service.execute(new Command(order));

        assertThat(service.execute(new Command(UUID.randomUUID()))).isEqualTo(new Result(2));
        assertThat(outbox.size()).isEqualTo(4);
    }
}
