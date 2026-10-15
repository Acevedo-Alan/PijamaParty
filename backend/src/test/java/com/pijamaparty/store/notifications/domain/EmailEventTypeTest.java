package com.pijamaparty.store.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T-E3-22: only an email that never reached its recipient is bounced; a complaint or a transient bounce is not. */
class EmailEventTypeTest {

    @Test
    void givenEachEventType_whenAskedWhetherItBouncesTheEmail_thenOnlyAPermanentBounceAndASuppressionDo() {
        assertThat(EmailEventType.PERMANENT_BOUNCE.bouncesTheEmail()).isTrue();
        assertThat(EmailEventType.SUPPRESSED.bouncesTheEmail()).isTrue();
        assertThat(EmailEventType.TRANSIENT_BOUNCE.bouncesTheEmail()).isFalse();
        assertThat(EmailEventType.COMPLAINT.bouncesTheEmail()).isFalse();
    }
}
