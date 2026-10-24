package com.pijamaparty.store.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityValuesTest {

    @Test
    void givenWholePesos_whenWritten_thenTheThousandsAreSeparatedWithPointsAndThereAreNoCents() {
        assertThat(ActivityValues.pesos(Money.of(2_200_000))).isEqualTo("$22.000");
        assertThat(ActivityValues.pesos(Money.of(100_000_000))).isEqualTo("$1.000.000");
        assertThat(ActivityValues.pesos(Money.of(1))).isEqualTo("$0,01");
    }

    @Test
    void givenCents_whenWritten_thenTheyFollowAComma() {
        assertThat(ActivityValues.pesos(Money.of(2_200_050))).isEqualTo("$22.000,50");
    }

    @Test
    void givenSeveralValues_whenListed_thenTheyAreCommaSeparated() {
        assertThat(ActivityValues.list(List.of("S", "M", "L"))).isEqualTo("S, M, L");
    }
}
