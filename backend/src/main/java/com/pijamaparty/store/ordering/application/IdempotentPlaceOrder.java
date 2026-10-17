package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.CheckoutForm;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.application.IdempotencyKeys;
import com.pijamaparty.store.platform.application.IdempotencyKeys.StoredAnswer;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.IdempotencyScope;
import com.pijamaparty.store.platform.domain.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-E3-14 (RNF-20.1, RNF-20.2): {@link PlaceOrder} once per {@code Idempotency-Key}. Decorates
 * {@link PlaceOrderService}, which joins its transaction, so the key and the order it placed commit together or not
 * at all. A repetition repeats no effect: it gets the first order, with a new tracking token of its own.
 */
@Service
class IdempotentPlaceOrder implements PlaceOrder {

    /** How long a key answers; the daily purge deletes it afterwards. */
    static final Duration KEY_LIFETIME = Duration.ofHours(24);

    private static final int CREATED = 201;
    private static final String NUMBER = "number";
    private static final String TOTAL_CENTS = "totalCents";
    private static final String RESERVATION_EXPIRES_AT = "reservationExpiresAt";

    private final PlaceOrderService placeOrder;
    private final IdempotencyKeys keys;
    private final SaveTrackingLinks trackingLinks;
    private final Clock clock;

    IdempotentPlaceOrder(PlaceOrderService placeOrder, IdempotencyKeys keys, SaveTrackingLinks trackingLinks,
                         Clock clock) {
        this.placeOrder = placeOrder;
        this.keys = keys;
        this.trackingLinks = trackingLinks;
        this.clock = clock;
    }

    /**
     * As the buyer: Row Level Security lets it read and record only the keys of its own scope, and add its tracking
     * token only to the order of one of them (RNF-27).
     */
    @Override
    @Transactional
    public Result execute(Command command) {
        Optional<IdempotencyScope> scope = IdempotencyScope.of(command.actor());
        if (scope.isEmpty()) {
            // Staff, or a guest without a cart: no cart to buy, so nothing to place or to store.
            return placeOrder.execute(command);
        }
        String requestHash = requestHashOf(command);
        switch (keys.claim(scope.get(), command.idempotencyKey())) {
            case IdempotencyKeys.Claim.Busy busy -> {
                return new Result.KeyInUse();
            }
            case IdempotencyKeys.Claim.Answered answered -> {
                return answered.answer().requestHash().equals(requestHash)
                    ? repeat(command, answered.answer())
                    : new Result.KeyReused();
            }
            case IdempotencyKeys.Claim.Fresh fresh -> {
                // The key is this transaction's until it ends.
            }
        }
        Result result = placeOrder.execute(command);
        if (result instanceof Result.Placed placed) {
            Instant now = clock.instant();
            keys.record(scope.get(), command.idempotencyKey(), answerOf(placed, requestHash), now,
                now.plus(KEY_LIFETIME));
        }
        // A refusal wrote nothing, so there is nothing to protect from a repetition.
        return result;
    }

    /** The first order again, reachable with the tracking token of this request (RNF-29.6). */
    private Result repeat(Command command, StoredAnswer answer) {
        TrackingToken trackingToken = command.actor().trackingToken().orElseThrow(() ->
            new IllegalStateException("the checkout binds a new tracking token before it begins"));
        OrderId order = OrderId.of(answer.resourceId());
        if (!trackingLinks.add(order, trackingToken.hash(), clock.instant())) {
            throw new IllegalStateException("the order of a stored checkout answer is kept (R-15)");
        }
        Map<String, String> body = answer.body();
        return new Result.Placed(order, Long.parseLong(body.get(NUMBER)),
            Money.of(Long.parseLong(body.get(TOTAL_CENTS))), Instant.parse(body.get(RESERVATION_EXPIRES_AT)));
    }

    /** What the checkout answered, without the tracking token: only its hash is ever stored (RNF-29.6). */
    private static StoredAnswer answerOf(Result.Placed placed, String requestHash) {
        return new StoredAnswer(requestHash, placed.orderId().value(), CREATED, Map.of(
            NUMBER, Long.toString(placed.number()),
            TOTAL_CENTS, Long.toString(placed.total().cents()),
            RESERVATION_EXPIRES_AT, placed.reservationExpiresAt().toString()));
    }

    /**
     * SHA-256 of the command's content in a canonical encoding: every field in a fixed order, as its length and its
     * text, or a mark for none. Neither JSON formatting nor field order changes it; the cart is not part of it, since
     * the scope already ties the key to the cart, and placing the order empties it. {@code saveAddress}
     * counts only when true.
     */
    static String requestHashOf(Command command) {
        CheckoutForm form = command.form();
        var canonical = new StringBuilder();
        for (String field : new String[] {form.name(), form.email(), form.phone(), form.nationalId(), form.street(),
            form.number(), form.floor(), form.apartment(), form.city(), form.postalCode(), command.province().name(),
            command.paymentProvider().name(), Boolean.toString(command.acceptedTerms())}) {
            if (field == null) {
                canonical.append("-;");
            } else {
                canonical.append(field.length()).append(':').append(field).append(';');
            }
        }
        // T-E4-06: only when true, so every request without it keeps the hash it had before (keys live 24 hours).
        if (command.saveAddress()) {
            canonical.append("saveAddress;");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
