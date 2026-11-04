package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.catalog.domain.StockFilter;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-07 (T-E8-09): the "Stock" screen of the back office. */
class ListStockOverviewServiceTest {

    private final ProductFixture fixture = new ProductFixture();
    private final InMemoryStockOverviewPages pages = new InMemoryStockOverviewPages(fixture.products);
    private final InMemoryLowStockThreshold threshold = new InMemoryLowStockThreshold();
    private final ListStockOverview list = new ListStockOverviewService(pages, threshold);

    @Test
    void givenAVariantWith2Available_whenFilteredByLow_thenTheThresholdOfTheStoreSettingsDecides() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));
        pages.setOnHand(product.variants().getFirst().id(), 2);

        assertThat(sizes(list.execute(query(StockFilter.LOW, Optional.empty())))).containsExactly("M");
        threshold.set(1);
        assertThat(sizes(list.execute(query(StockFilter.LOW, Optional.empty())))).isEmpty();
        assertThat(sizes(list.execute(query(StockFilter.ALL, Optional.empty())))).containsExactly("M");
    }

    @Test
    void givenMoreThanAPageOfVariants_whenListed_thenPagesOf50ComeWithWhetherMoreFollow() {
        fixture.withMatrix("Pijama", IntStream.rangeClosed(1, 17).mapToObj(i -> "T" + i).toList(),
            List.of("Rosa", "Azul", "Verde"));

        var first = (ListStockOverview.Result.Listed) list.execute(query(StockFilter.ALL, Optional.empty()));
        var second = (ListStockOverview.Result.Listed) list.execute(
            new ListStockOverview.Query(ADMIN, StockFilter.ALL, Optional.empty(), Optional.empty(), 1));

        assertThat(first.rows()).hasSize(ListStockOverview.PAGE_SIZE);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.rows()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void givenSearches_whenListed_thenOneOf1CharacterOrOver100IsInvalidAndABlankOneIsNoSearch() {
        fixture.withMatrix("Pijama Corazón", List.of("M"), List.of("Rosa"));

        assertThat(list.execute(query(StockFilter.ALL, Optional.of("a"))))
            .isEqualTo(new ListStockOverview.Result.InvalidSearch());
        assertThat(list.execute(query(StockFilter.ALL, Optional.of("a".repeat(101)))))
            .isEqualTo(new ListStockOverview.Result.InvalidSearch());
        assertThat(sizes(list.execute(query(StockFilter.ALL, Optional.of("  "))))).containsExactly("M");
        assertThat(sizes(list.execute(query(StockFilter.ALL, Optional.of(" corazon "))))).containsExactly("M");
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenListing_thenItIsForbidden() {
        assertThat(list.execute(new ListStockOverview.Query(CUSTOMER, StockFilter.ALL, Optional.empty(),
            Optional.empty(), 0))).isEqualTo(new ListStockOverview.Result.Forbidden());
        assertThatThrownBy(() -> new ListStockOverview.Query(ADMIN, StockFilter.ALL, Optional.empty(),
            Optional.empty(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ListStockOverview.Query query(StockFilter filter, Optional<String> search) {
        return new ListStockOverview.Query(ADMIN, filter, search, Optional.empty(), 0);
    }

    private static List<String> sizes(ListStockOverview.Result result) {
        return ((ListStockOverview.Result.Listed) result).rows().stream().map(StockOverviewRow::size).toList();
    }
}
