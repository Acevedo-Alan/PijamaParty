package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewForm;
import com.pijamaparty.store.reviews.domain.ReviewId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs as the customer itself: the database shows it only its own orders (V14), account (V29) and reviews (V37), and
 * lets it write only its own reviews, pending moderation.
 */
@Service
class SubmitReviewService implements SubmitReview {

    private final ProductVariants productVariants;
    private final PurchasedProducts purchases;
    private final CustomerNames customerNames;
    private final LoadReview loadReview;
    private final SaveReview saveReview;
    private final IdGenerator ids;
    private final Clock clock;

    SubmitReviewService(ProductVariants productVariants, PurchasedProducts purchases, CustomerNames customerNames,
                        LoadReview loadReview, SaveReview saveReview, IdGenerator ids, Clock clock) {
        this.productVariants = productVariants;
        this.purchases = purchases;
        this.customerNames = customerNames;
        this.loadReview = loadReview;
        this.saveReview = saveReview;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.CUSTOMER) || command.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        UserId customer = command.actor().userId().get();
        ReviewContent content;
        switch (command.review().validate()) {
            case ReviewForm.Outcome.Invalid invalid -> {
                return new Result.InvalidReview(invalid.fields());
            }
            case ReviewForm.Outcome.Valid valid -> content = valid.content();
        }
        Optional<Set<UUID>> variants = productVariants.ofProduct(command.product());
        if (variants.isEmpty()) {
            return new Result.ProductNotFound();
        }
        // Checked on every submission, editing too; a review stays if its order is cancelled later.
        if (!purchases.boughtAnyOf(customer, variants.get())) {
            return new Result.NotABuyer();
        }
        Optional<AuthorName> authorName = customerNames.nameOf(customer).flatMap(AuthorName::fromAccountName);
        if (authorName.isEmpty()) {
            return new Result.NotSignedIn();
        }
        Instant now = clock.instant();
        Review review = loadReview.findByAuthorAndProduct(customer, command.product())
            .map(existing -> existing.resubmit(content, authorName.get(), now))
            .orElseGet(() -> Review.submit(ReviewId.of(ids.newId()), command.product(), customer, content,
                authorName.get(), now));
        return switch (saveReview.save(review)) {
            case CREATED -> new Result.Created(MyReview.of(review));
            case REPLACED -> new Result.Replaced(MyReview.of(review));
        };
    }
}
