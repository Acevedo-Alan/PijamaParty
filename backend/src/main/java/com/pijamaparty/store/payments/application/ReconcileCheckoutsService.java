package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * UC-15 (T-E3b-03). Deliberately not transactional: the checkouts are read in one short transaction, and each is then
 * confirmed on its own, so no database connection waits for the provider (RNF-21).
 */
@Service
class ReconcileCheckoutsService implements ReconcileCheckouts {

    /** A younger checkout is still being paid, or its notification is on the way. */
    static final Duration MIN_AGE = Duration.ofMinutes(1);
    /** How long after it expires a checkout is still asked for: MODO may finish a payment that started in time. */
    static final Duration AFTER_EXPIRY = Duration.ofMinutes(15);
    /** Far above what a minute brings at fewer than 20 orders a day, and far below MODO's 600 queries in 10 minutes. */
    static final int MAX_PER_RUN = 50;
    /** The payments that may still be paid: not final (RNF-20.5). */
    static final Set<PaymentStatus> OPEN_STATUSES = Arrays.stream(PaymentStatus.values())
        .filter(status -> !status.isFinal())
        .collect(Collectors.toUnmodifiableSet());

    private final Set<PaymentProvider> providers;
    private final CheckoutsToReconcile checkouts;
    private final ConfirmPayment confirmPayment;
    private final Clock clock;

    ReconcileCheckoutsService(List<PaymentVerifier> verifiers, CheckoutsToReconcile checkouts,
                              ConfirmPayment confirmPayment, Clock clock) {
        this.providers = EnumSet.noneOf(PaymentProvider.class);
        for (PaymentVerifier verifier : verifiers) {
            if (verifier.verifiesByCheckout()) {
                providers.add(verifier.provider());
            }
        }
        this.checkouts = checkouts;
        this.confirmPayment = confirmPayment;
        this.clock = clock;
    }

    @Override
    public Result execute() {
        if (providers.isEmpty()) {
            return new Result(List.of());
        }
        Instant now = clock.instant();
        return new Result(checkouts.due(providers, OPEN_STATUSES, now.minus(MIN_AGE), now.minus(AFTER_EXPIRY),
                MAX_PER_RUN).stream()
            .map(checkout -> new Reconciled(checkout.provider(), checkout.checkoutId(),
                confirmPayment.execute(new ConfirmPayment.Command(checkout.provider(), checkout.checkoutId()))))
            .toList());
    }
}
