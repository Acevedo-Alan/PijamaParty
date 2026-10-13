package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.api.BuyerEmailStatus.Problem;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindBuyerEmailProblemServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final FindBuyerEmailProblemService service = new FindBuyerEmailProblemService(outbox);
    private final UUID order = UUID.randomUUID();

    @Test
    void givenTheBuyersEmailFailed_whenAskedAbout_thenItIsAProblem() {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        OutboxEmail email = outbox.lockNextDue(NOW).orElseThrow();
        outbox.failed(email.id(), NOW);

        assertThat(service.problemOf(order)).contains(Problem.FAILED);
    }

    @Test
    void givenTheBuyersEmailPendingOrSent_whenAskedAbout_thenThereIsNoProblem() {
        UUID sent = UUID.randomUUID();
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, sent, NOW.plusSeconds(1));
        outbox.sent(outbox.find(EmailType.ORDER_PAID_CUSTOMER, sent).orElseThrow().id(), "re_1", NOW);

        assertThat(service.problemOf(order)).isEmpty();
        assertThat(service.problemOf(sent)).isEmpty();
        assertThat(service.problemOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    void givenOnlyTheAdministratorsEmailFailed_whenAskedAbout_thenTheBuyerHasNoProblem() {
        outbox.enqueue(EmailType.ORDER_PAID_ADMIN, order, NOW);
        outbox.failed(outbox.lockNextDue(NOW).orElseThrow().id(), NOW);

        assertThat(service.problemOf(order)).isEmpty();
    }
}
