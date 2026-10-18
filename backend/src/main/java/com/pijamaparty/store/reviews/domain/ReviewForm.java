package com.pijamaparty.store.reviews.domain;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * UC-24: a review as typed, before its rules apply.
 *
 * @param rating as sent, so a fraction such as 4.5 is refused instead of cut; null when it was not sent
 * @param text null or blank for none; surrounding spaces are dropped
 */
public record ReviewForm(BigDecimal rating, String text) {

    /** Every field that breaks a rule, or the content when none does. */
    public Outcome validate() {
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        Optional<Integer> wholeRating = wholeRating().filter(ReviewContent::isValidRating);
        if (wholeRating.isEmpty()) {
            invalid.add(Field.RATING);
        }
        Optional<String> trimmed = Optional.ofNullable(text).map(String::strip).filter(value -> !value.isEmpty());
        if (trimmed.isPresent() && !ReviewContent.isValidText(trimmed.get())) {
            invalid.add(Field.TEXT);
        }
        if (!invalid.isEmpty()) {
            return new Outcome.Invalid(invalid);
        }
        return new Outcome.Valid(new ReviewContent(wholeRating.get(), trimmed));
    }

    private Optional<Integer> wholeRating() {
        if (rating == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(rating.intValueExact());
        } catch (ArithmeticException fractionOrTooLarge) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "ReviewForm[rating=" + rating + ", text=***]";
    }

    public enum Field {
        RATING,
        TEXT
    }

    public sealed interface Outcome {

        record Valid(ReviewContent content) implements Outcome {}

        record Invalid(Set<Field> fields) implements Outcome {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }
    }
}
