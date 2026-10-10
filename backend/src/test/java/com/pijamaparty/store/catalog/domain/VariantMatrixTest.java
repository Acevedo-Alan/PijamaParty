package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.VariantMatrix.Parsed;
import com.pijamaparty.store.catalog.domain.VariantMatrix.Problem;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VariantMatrixTest {

    @Test
    void givenSizesAndColorsAsTyped_whenParsed_thenTheyAreTrimmedAndKeepTheirOrder() {
        Parsed parsed = VariantMatrix.parse(List.of(" S", "M ", "L"), List.of("Rosa", " Celeste "));

        assertThat(parsed).isEqualTo(new Parsed.Valid(new VariantMatrix.Request(
            List.of(new Size("S"), new Size("M"), new Size("L")), List.of(new Color("Rosa"), new Color("Celeste")))));
    }

    @Test
    void givenAnInvalidOrMissingValue_whenParsed_thenTheProblemIsNamed() {
        assertThat(VariantMatrix.parse(List.of("M", " "), List.of("Rosa"))).isEqualTo(invalid(Problem.INVALID_SIZE));
        assertThat(VariantMatrix.parse(List.of("M"), List.of("a".repeat(31))))
            .isEqualTo(invalid(Problem.INVALID_COLOR));
        assertThat(VariantMatrix.parse(List.of(), List.of("Rosa"))).isEqualTo(invalid(Problem.NO_SIZES));
        assertThat(VariantMatrix.parse(List.of("M"), List.of())).isEqualTo(invalid(Problem.NO_COLORS));
    }

    @Test
    void givenRepeatedValuesAfterTrimming_whenParsed_thenTheyAreRejected() {
        assertThat(VariantMatrix.parse(List.of("M", " M"), List.of("Rosa")))
            .isEqualTo(invalid(Problem.REPEATED_SIZES));
        assertThat(VariantMatrix.parse(List.of("M"), List.of("Rosa", "Rosa ")))
            .isEqualTo(invalid(Problem.REPEATED_COLORS));
    }

    @Test
    void givenMoreThan20SizesOrColors_whenParsed_thenTheyAreRejected() {
        assertThat(VariantMatrix.parse(values(20), values(20))).isInstanceOf(Parsed.Valid.class);
        assertThat(VariantMatrix.parse(values(21), List.of("Rosa"))).isEqualTo(invalid(Problem.TOO_MANY_SIZES));
        assertThat(VariantMatrix.parse(List.of("M"), values(21))).isEqualTo(invalid(Problem.TOO_MANY_COLORS));
    }

    private static Parsed invalid(Problem problem) {
        return new Parsed.Invalid(problem);
    }

    private static List<String> values(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "v" + i).toList();
    }
}
