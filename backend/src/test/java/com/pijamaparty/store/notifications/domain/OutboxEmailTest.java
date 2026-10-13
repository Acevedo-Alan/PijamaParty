package com.pijamaparty.store.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.notifications.domain.OutboxEmail.Claim;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutboxEmailTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:30Z");
    private static final UUID ID = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();

    @Test
    void givenANewEmail_whenClaimed_thenItIsTheFirstAttemptRetriedAMinuteLater() {
        var email = new OutboxEmail(ID, EmailType.ORDER_PAID_CUSTOMER, ORDER, 0);

        assertThat(email.claim(NOW)).isEqualTo(new Claim.Attempt(
            new OutboxEmail(ID, EmailType.ORDER_PAID_CUSTOMER, ORDER, 1), NOW.plusSeconds(60)));
    }

    @Test
    void givenAnEmailTriedFourTimes_whenClaimed_thenItIsTheLastAttempt() {
        var email = new OutboxEmail(ID, EmailType.ORDER_PAID_ADMIN, ORDER, 4);

        Claim claim = email.claim(NOW);

        assertThat(claim).isInstanceOf(Claim.Attempt.class);
        assertThat(claim.email().attempts()).isEqualTo(5);
        assertThat(claim.email().isLastAttempt()).isTrue();
    }

    @Test
    void givenAnEmailWithItsFiveAttemptsSpent_whenClaimed_thenItIsExhaustedAndNotSent() {
        var email = new OutboxEmail(ID, EmailType.ORDER_PAID_CUSTOMER, ORDER, 5);

        assertThat(email.claim(NOW)).isEqualTo(new Claim.Exhausted(email));
    }

    @Test
    void givenAnEmailWithAttemptsLeft_whenAskedIfLast_thenItIsNot() {
        assertThat(new OutboxEmail(ID, EmailType.ORDER_PAID_CUSTOMER, ORDER, 1).isLastAttempt()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6})
    void givenAttemptsOutOfRange_whenCreated_thenItFails(int attempts) {
        assertThatThrownBy(() -> new OutboxEmail(ID, EmailType.ORDER_PAID_CUSTOMER, ORDER, attempts))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
