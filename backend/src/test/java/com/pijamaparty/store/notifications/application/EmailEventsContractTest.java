package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Contract of {@link EmailEvents} (UC-17, T-E3-22, RNF-20.3): a notification is recorded once per event id. */
public abstract class EmailEventsContractTest<T extends EmailEvents> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");
    protected static final MailProvider RESEND = MailProvider.RESEND;

    @Test
    void givenANewNotification_whenRecorded_thenItIsToBeProcessed() {
        assertThat(subject.recordFirst(RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW)).isTrue();
    }

    @Test
    void givenARecordedNotification_whenItsRetryIsRecorded_thenItWasAlreadyProcessed() {
        subject.recordFirst(RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW);
        persistAndForget();

        assertThat(subject.recordFirst(RESEND, "msg_1", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW.plusSeconds(5)))
            .isFalse();
    }

    @Test
    void givenANotificationOfAnEmailRecorded_whenAnotherNotificationOfItIsRecorded_thenItIsToBeProcessed() {
        subject.recordFirst(RESEND, "msg_1", EmailEventType.TRANSIENT_BOUNCE, "re_1", NOW);
        persistAndForget();

        assertThat(subject.recordFirst(RESEND, "msg_2", EmailEventType.PERMANENT_BOUNCE, "re_1", NOW.plusSeconds(60)))
            .isTrue();
    }
}
