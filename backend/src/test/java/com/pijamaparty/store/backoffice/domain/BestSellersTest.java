package com.pijamaparty.store.backoffice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.domain.BestSellers.Sold;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class BestSellersTest {

    @Test
    void givenAProductSoldInTwoSizes_whenRanked_thenItShowsOnceWithItsUnitsAddedUp() {
        List<Sold> perVariant = List.of(
            new Sold("luna", "Pijama Luna", 3, Money.of(6_000_000)),
            new Sold("luna", "Pijama Luna", 2, Money.of(4_000_000)),
            new Sold("sol", "Camisón Sol", 4, Money.of(4_000_000)));

        assertThat(BestSellers.top(perVariant, 5)).containsExactly(
            new Sold("luna", "Pijama Luna", 5, Money.of(10_000_000)),
            new Sold("sol", "Camisón Sol", 4, Money.of(4_000_000)));
    }

    @Test
    void givenTiesInUnits_whenRanked_thenTheLargerAmountAndThenTheNameGoFirst() {
        List<Sold> perVariant = List.of(
            new Sold("b", "Bata", 2, Money.of(1_000_000)),
            new Sold("a", "Antifaz", 2, Money.of(1_000_000)),
            new Sold("c", "Camisón", 2, Money.of(3_000_000)));

        assertThat(BestSellers.top(perVariant, 5)).extracting(Sold::productName)
            .containsExactly("Camisón", "Antifaz", "Bata");
    }

    @Test
    void givenSevenProducts_whenRanked_thenOnlyTheFiveFirstShow() {
        List<Sold> perVariant = List.of(
            new Sold("1", "Uno", 7, Money.of(100)), new Sold("2", "Dos", 6, Money.of(100)),
            new Sold("3", "Tres", 5, Money.of(100)), new Sold("4", "Cuatro", 4, Money.of(100)),
            new Sold("5", "Cinco", 3, Money.of(100)), new Sold("6", "Seis", 2, Money.of(100)),
            new Sold("7", "Siete", 1, Money.of(100)));

        assertThat(BestSellers.top(perVariant, 5)).extracting(Sold::productSlug)
            .containsExactly("1", "2", "3", "4", "5");
    }
}
