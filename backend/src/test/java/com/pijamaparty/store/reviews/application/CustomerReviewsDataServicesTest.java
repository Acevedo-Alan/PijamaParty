package com.pijamaparty.store.reviews.application;

import static com.pijamaparty.store.reviews.application.ReviewsFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.reviews.application.ExportCustomerReviews.ExportedReview;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-28 (T-E5-06): a customer's reviews in its data download, and deleted with its account. */
class CustomerReviewsDataServicesTest {

    private final ReviewsFixture fixture = new ReviewsFixture();

    @Test
    void givenTwoReviewsOneOfAProductTheCatalogNoLongerHas_whenExported_thenBothComeOldestFirst() {
        UUID gone = UUID.randomUUID();
        ProductId sol = ProductId.of(UUID.randomUUID());
        fixture.catalog.add(sol, "Camisón Sol", Set.of(gone));
        fixture.purchases.bought(fixture.anaId, gone);
        fixture.submitted(fixture.ana, sol, 4, null);
        fixture.clock.advance(Duration.ofHours(1));
        fixture.submitted(fixture.ana, fixture.luna, 5, "Muy cómodo");
        fixture.submitted(fixture.bruno, fixture.luna, 1, null);
        var removedFromCatalog = new InMemoryCatalogProducts();
        removedFromCatalog.add(fixture.luna, "Pijama Luna", Set.of(fixture.lunaRosaM));
        var export = new ExportCustomerReviewsService(fixture.reviews, removedFromCatalog);

        assertThat(export.execute(new ExportCustomerReviews.Query(fixture.anaId))).containsExactly(
            new ExportedReview(Optional.empty(), 4, Optional.empty(), ReviewStatus.PENDING, T0, T0),
            new ExportedReview(Optional.of("Pijama Luna"), 5, Optional.of("Muy cómodo"), ReviewStatus.PENDING,
                T0.plusSeconds(3_600), T0.plusSeconds(3_600)));
    }

    @Test
    void givenACustomerWithoutReviews_whenExported_thenThereAreNone() {
        assertThat(fixture.export.execute(new ExportCustomerReviews.Query(fixture.anaId))).isEmpty();
    }

    @Test
    void givenACustomerWithAReview_whenItsAccountIsDeletedTwice_thenItsReviewGoesOnceAndOthersStay() {
        fixture.submitted(fixture.ana, fixture.luna, 5, null);
        fixture.purchases.bought(fixture.brunoId, fixture.lunaRosaM);
        fixture.submitted(fixture.bruno, fixture.luna, 3, null);

        assertThat(fixture.delete.execute(new DeleteCustomerReviews.Command(fixture.anaId)))
            .isEqualTo(new DeleteCustomerReviews.Result.Deleted(1));
        assertThat(fixture.delete.execute(new DeleteCustomerReviews.Command(fixture.anaId)))
            .isEqualTo(new DeleteCustomerReviews.Result.NoReviews());
        assertThat(fixture.reviews.all()).singleElement()
            .satisfies(review -> assertThat(review.author()).isEqualTo(fixture.brunoId));
    }
}
