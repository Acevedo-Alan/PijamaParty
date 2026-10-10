package com.pijamaparty.store.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiTest {

    @Test
    void givenAnEmail_whenMasking_thenOnlyTheDomainRemains() {
        assertThat(Pii.email("Ana.Perez@Gmail.com")).isEqualTo("***@gmail.com");
        assertThat(Pii.email("not-an-email")).isEqualTo("***");
    }

    @Test
    void givenANationalIdOrAnAddress_whenMasking_thenNothingRemains() {
        assertThat(Pii.nationalId("30123456")).isEqualTo("***");
        assertThat(Pii.address("San Martín 1234, Santa Fe")).isEqualTo("***");
    }

    @Test
    void givenNull_whenMasking_thenNullIsReturned() {
        assertThat(Pii.email(null)).isNull();
        assertThat(Pii.nationalId(null)).isNull();
        assertThat(Pii.scrub(null)).isNull();
    }

    @Test
    void givenFreeTextWithPersonalData_whenScrubbing_thenEmailsAndNationalIdsAreMasked() {
        String text = "duplicate key (email, dni)=(ana@gmail.com, 30.123.456) cuit 20-30123456-7 dni 30123456";

        String scrubbed = Pii.scrub(text);

        assertThat(scrubbed)
            .doesNotContain("ana@", "30.123.456", "30123456", "20-30123456-7")
            .contains("***@gmail.com");
    }

    @Test
    void givenAnEmailAtTheEndOfASentence_whenScrubbing_thenTheFinalDotIsKept() {
        assertThat(Pii.scrub("Bounced: juan@mail.com.ar.")).isEqualTo("Bounced: ***@mail.com.ar.");
    }

    @Test
    void givenAnExceptionChainWithPersonalData_whenScrubbing_thenTypesAndStackTracesStayAndPersonalDataGoes() {
        var cause = new IllegalArgumentException("duplicate email ana@gmail.com");
        var exception = new IllegalStateException("insert failed for dni 30123456", cause);

        Throwable scrubbed = Pii.scrubException(exception);

        assertThat(scrubbed.getMessage()).startsWith("java.lang.IllegalStateException").doesNotContain("30123456");
        assertThat(scrubbed.getStackTrace()).isEqualTo(exception.getStackTrace());
        assertThat(scrubbed.getCause().getMessage())
            .startsWith("java.lang.IllegalArgumentException").contains("***@gmail.com").doesNotContain("ana@");
    }

    @Test
    void givenADatabaseErrorQuotingTheFailingRow_whenScrubbing_thenOnlyTheTypeAndSqlStateRemain() {
        var sql = new java.sql.SQLException(
            "ERROR: null value in column \"phone\" violates not-null constraint. Failing row contains "
                + "(Ana Perez, San Martin 1234, Santa Fe)", "23502");
        var exception = new org.springframework.dao.DataIntegrityViolationException("could not execute statement", sql);

        Throwable scrubbed = Pii.scrubException(exception);

        assertThat(scrubbed.getMessage()).isEqualTo("org.springframework.dao.DataIntegrityViolationException");
        assertThat(scrubbed.getCause().getMessage()).isEqualTo("java.sql.SQLException (SQLState 23502)");
    }

    @Test
    void givenFreeTextWithoutPersonalData_whenScrubbing_thenUuidsAndSmallNumbersAreKept() {
        String text = "order 1042 for 3 items, id 12345678-1234-1234-1234-123456789012";

        assertThat(Pii.scrub(text)).isEqualTo(text);
    }
}
