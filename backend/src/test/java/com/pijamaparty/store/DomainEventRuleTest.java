package com.pijamaparty.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.DomainEvent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves that {@code ArchitectureTest.domain_events_carry_no_personal_data} rejects what it must. */
class DomainEventRuleTest {

    record OrderPaid(UUID orderId, long totalCents) implements DomainEvent {
    }

    record OrderPaidWithEmail(UUID orderId, String customerEmail) implements DomainEvent {
    }

    record OrderShippedWithToken(UUID orderId, TrackingToken tracking) implements DomainEvent {
    }

    @Test
    void givenAnEventWithIdsOnly_whenCheckingTheRule_thenItPasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(OrderPaid.class);

        assertThat(ArchitectureTest.domain_events_carry_no_personal_data.evaluate(classes).hasViolation()).isFalse();
    }

    @Test
    void givenAnEventWithAnEmail_whenCheckingTheRule_thenItFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(OrderPaidWithEmail.class);

        assertThat(ArchitectureTest.domain_events_carry_no_personal_data.evaluate(classes).hasViolation()).isTrue();
    }

    @Test
    void givenAnEventWithAnAccessToken_whenCheckingTheRule_thenItFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(OrderShippedWithToken.class);

        assertThat(ArchitectureTest.domain_events_carry_no_personal_data.evaluate(classes).hasViolation()).isTrue();
    }
}
