package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.MovableClock;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.ReviewForm;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The review use cases over in-memory fakes: Ana bought a variant of Pijama Luna, Bruno bought nothing. */
class ReviewsFixture {

    static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");

    final MovableClock clock = new MovableClock(T0);
    final InMemoryReviewStore reviews = new InMemoryReviewStore();
    final InMemoryCatalogProducts catalog = new InMemoryCatalogProducts();
    final InMemoryPurchasedProducts purchases = new InMemoryPurchasedProducts();
    final InMemoryCustomerNames names = new InMemoryCustomerNames();

    final SubmitReview submit = new SubmitReviewService(catalog, purchases, names, reviews, reviews,
        new InMemoryIdGenerator(), clock);
    final GetMyReview get = new GetMyReviewService(catalog, purchases, reviews);
    final DeleteCustomerReviews delete = new DeleteCustomerReviewsService(reviews);
    final ExportCustomerReviews export = new ExportCustomerReviewsService(reviews, catalog);
    final ListApprovedReviews listApproved = new ListApprovedReviewsService(catalog, reviews);

    final UserId anaId = UserId.of(UUID.randomUUID());
    final UserId brunoId = UserId.of(UUID.randomUUID());
    final Actor ana = customer(anaId);
    final Actor bruno = customer(brunoId);
    final ProductId luna = ProductId.of(UUID.randomUUID());
    final UUID lunaRosaM = UUID.randomUUID();

    ReviewsFixture() {
        catalog.add(luna, "Pijama Luna", Set.of(lunaRosaM, UUID.randomUUID()));
        catalog.list(luna, "pijama-luna");
        names.add(anaId, "Ana María Pérez");
        names.add(brunoId, "Bruno Díaz");
        purchases.bought(anaId, lunaRosaM);
    }

    static Actor customer(UserId id) {
        return Actor.customer(id, Optional.empty(), Optional.empty());
    }

    static ReviewForm form(int rating, String text) {
        return new ReviewForm(BigDecimal.valueOf(rating), text);
    }

    SubmitReview.Result submitted(Actor actor, ProductId product, int rating, String text) {
        return submit.execute(new SubmitReview.Command(actor, product, form(rating, text)));
    }
}
