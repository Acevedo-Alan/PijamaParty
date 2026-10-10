package com.pijamaparty.store.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

class SaturationUnitTest {

    private final Saturation saturation = new Saturation(Duration.ofSeconds(5));

    @Test
    void givenAnExhaustedConnectionPoolDeepInTheCauseChain_whenClassifying_thenItIsSaturation() {
        var exception = new IllegalStateException("wrapped",
            new CannotGetJdbcConnectionException("no connection", new SQLTransientConnectionException("timeout")));

        assertThat(saturation.causedBy(exception)).isTrue();
    }

    @Test
    void givenABoundedExecutorThatRejectsWork_whenClassifying_thenItIsSaturation() {
        assertThat(saturation.causedBy(new TaskRejectedException("queue full"))).isTrue();
        assertThat(saturation.causedBy(new java.util.concurrent.RejectedExecutionException("queue full"))).isTrue();
    }

    @Test
    void givenAnUnrelatedFailureOrNothing_whenClassifying_thenItIsNotSaturation() {
        assertThat(saturation.causedBy(new IllegalStateException("bug"))).isFalse();
        assertThat(saturation.causedBy(null)).isFalse();
    }

    @Test
    void givenARetryAfterBelowOneSecond_whenConfiguring_thenStartupFails() {
        assertThatThrownBy(() -> new Saturation(Duration.ofMillis(500))).isInstanceOf(IllegalArgumentException.class);
    }
}
