package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.OrderAlert.BuyerEmailProblem;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake of {@link BuyerEmails}: the orders whose buyer did not get the email. */
public class InMemoryBuyerEmails implements BuyerEmails {

    private final Map<UUID, BuyerEmailProblem> problems = new HashMap<>();

    /** Test arrangement: the buyer of the order did not get its email. */
    public synchronized void failed(UUID orderId) {
        problems.put(orderId, BuyerEmailProblem.FAILED);
    }

    @Override
    public synchronized Optional<BuyerEmailProblem> problemOf(UUID orderId) {
        return Optional.ofNullable(problems.get(orderId));
    }
}
