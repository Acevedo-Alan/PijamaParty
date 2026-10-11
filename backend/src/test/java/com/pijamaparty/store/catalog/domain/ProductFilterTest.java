package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-02: the filters of the public catalog. */
class ProductFilterTest {

    @Test
    void givenTextsAsSent_whenParsed_thenTheyAreTrimmedAndRepeatedValuesCountOnce() {
        Optional<ProductFilter> filter = parse(List.of(" M ", "M", "L"), List.of("Rosa, gris"), List.of("Algodón"),
            Optional.of(1000L), Optional.of(1000L));

        assertThat(filter).hasValueSatisfying(parsed -> {
            assertThat(parsed.sizes()).containsExactlyInAnyOrder(new Size("M"), new Size("L"));
            assertThat(parsed.colors()).containsExactly(new Color("Rosa, gris"));
            assertThat(parsed.materials()).containsExactly(new Material("Algodón"));
            assertThat(parsed.minPrice()).contains(Money.of(1000));
            assertThat(parsed.maxPrice()).contains(Money.of(1000));
        });
    }

    @Test
    void givenValuesThatCannotExist_whenParsed_thenTheFilterIsInvalid() {
        assertThat(parse(List.of(""), List.of(), List.of(), Optional.empty(), Optional.empty())).isEmpty();
        assertThat(parse(List.of(), List.of("x".repeat(Color.MAX_LENGTH + 1)), List.of(), Optional.empty(),
            Optional.empty())).isEmpty();
        assertThat(parse(List.of(), List.of(), List.of("Algo\u0000dón"), Optional.empty(), Optional.empty())).isEmpty();
    }

    @Test
    void givenMoreThan20Values_whenParsed_thenTheFilterIsInvalid() {
        List<String> twenty = IntStream.rangeClosed(1, ProductFilter.MAX_VALUES).mapToObj(i -> "T" + i).toList();
        List<String> twentyOne = IntStream.rangeClosed(1, ProductFilter.MAX_VALUES + 1).mapToObj(i -> "T" + i).toList();

        assertThat(parse(twenty, List.of(), List.of(), Optional.empty(), Optional.empty())).isPresent();
        assertThat(parse(twentyOne, List.of(), List.of(), Optional.empty(), Optional.empty())).isEmpty();
    }

    @Test
    void givenANegativePriceOrAMinimumAboveTheMaximum_whenParsed_thenTheFilterIsInvalid() {
        assertThat(parse(List.of(), List.of(), List.of(), Optional.of(-1L), Optional.empty())).isEmpty();
        assertThat(parse(List.of(), List.of(), List.of(), Optional.empty(), Optional.of(-1L))).isEmpty();
        assertThat(parse(List.of(), List.of(), List.of(), Optional.of(2000L), Optional.of(1999L))).isEmpty();
        assertThatThrownBy(() -> new ProductFilter(Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
            Optional.of(Money.of(2)), Optional.of(Money.of(1)))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Optional<ProductFilter> parse(List<String> sizes, List<String> colors, List<String> materials,
                                                 Optional<Long> min, Optional<Long> max) {
        return ProductFilter.parse(Optional.empty(), sizes, colors, materials, Set.of(), Set.of(), min, max);
    }
}
