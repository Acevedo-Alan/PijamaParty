package com.pijamaparty.store.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.joran.spi.JoranException;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * T-E6-03 (R-05): the application's own logback-spring.xml masks emails and national ids in every log line, in the
 * message and in the exception with its causes, whoever logged them.
 */
@ExtendWith(OutputCaptureExtension.class)
class PiiLogLayoutTest {

    private final LoggerContext context = new LoggerContext();
    /** A library's logger, which knows nothing of {@link Pii}. */
    private Logger library;

    @BeforeEach
    void configure() throws JoranException {
        context.setMDCAdapter(new LogbackMDCAdapter());
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
        library = context.getLogger("org.example.library");

    }

    @AfterEach
    void stop() {
        context.stop();
    }

    @Test
    void givenAMessageWithAnEmailAndANationalId_whenItIsLogged_thenBothAreMasked(CapturedOutput output) {
        library.warn("Could not save {} with DNI {}", "ana@gmail.com", "30.123.456");

        assertThat(output.getAll()).contains("Could not save ***@gmail.com with DNI ***")
            .doesNotContain("ana@", "30.123.456");
    }

    @Test
    void givenAnExceptionChainWithPersonalData_whenItIsLogged_thenItsTypesAndStackStayAndThePersonalDataGoes(
        CapturedOutput output) {
        var cause = new SQLException("Key (email)=(ana@gmail.com) already exists", "23505");
        var exception = new IllegalStateException("insert failed for dni 30123456", cause);

        library.error("Unexpected error", exception);

        assertThat(output.getAll())
            .contains("java.lang.IllegalStateException", "java.sql.SQLException (SQLState 23505)",
                "at " + getClass().getName())
            .doesNotContain("ana@gmail.com", "30123456");
    }

    @Test
    void givenAnExceptionTheCallerScrubbedAlready_whenItIsLogged_thenItIsWrittenOnce(CapturedOutput output) {
        library.error("Unexpected error", Pii.scrubException(new IllegalStateException("dni 30123456")));

        assertThat(output.getAll()).contains("Pii$ScrubbedException: java.lang.IllegalStateException: dni ***")
            .doesNotContain("ScrubbedException: " + Pii.ScrubbedException.class.getName());
    }
}
