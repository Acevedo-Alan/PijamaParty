package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake of {@link WithdrawableOrders}, {@link ActiveWithdrawals}, {@link SaveWithdrawals},
 * {@link WithdrawalNoticeDetails}, {@link WithdrawalPages}, {@link ActiveWithdrawalViews} and
 * {@link WithdrawalResolutions}: orders by number, arranged with {@link #place}, and their requests. Like the database,
 * one active request per order and each code once.
 */
public class InMemoryWithdrawalStore implements WithdrawableOrders, ActiveWithdrawals, SaveWithdrawals,
    WithdrawalNoticeDetails, WithdrawalPages, ActiveWithdrawalViews, WithdrawalResolutions {

    public static final String BUYER_FIRST_NAME = "Ana";
    public static final String BUYER_LAST_NAME = "Pérez";
    public static final String BUYER_EMAIL = "ana@example.com";

    private final Map<Long, WithdrawableOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, Stored> requests = new LinkedHashMap<>();
    private long nextNumber = 1001;

    /** A request as stored; {@code resolvedAt} empty while {@code RECEIVED}. */
    public record Stored(NewWithdrawal withdrawal, Optional<Instant> resolvedAt) {

        WithdrawalStatus status() {
            return resolvedAt.isPresent() ? WithdrawalStatus.RESOLVED : WithdrawalStatus.RECEIVED;
        }
    }

    /** An order of {@link #BUYER_FIRST_NAME} {@link #BUYER_LAST_NAME}, {@link #BUYER_EMAIL}; answers it with its number. */
    public synchronized WithdrawableOrder place(OrderStatus status, Instant createdAt, Optional<Instant> deliveredAt) {
        var order = new WithdrawableOrder(OrderId.of(UUID.randomUUID()), nextNumber++, status, createdAt, deliveredAt);
        orders.put(order.number(), order);
        return order;
    }

    public synchronized List<Stored> requests() {
        return List.copyOf(requests.values());
    }

    @Override
    public synchronized Optional<WithdrawableOrder> lock(long number, String email) {
        return BUYER_EMAIL.equals(email) ? Optional.ofNullable(orders.get(number)) : Optional.empty();
    }

    @Override
    public synchronized Optional<WithdrawalCode> codeOf(OrderId order) {
        return active(order).map(stored -> stored.withdrawal().code());
    }

    @Override
    public synchronized boolean insert(NewWithdrawal withdrawal) {
        if (requests.values().stream().anyMatch(stored -> stored.withdrawal().code().equals(withdrawal.code()))) {
            return false;
        }
        if (codeOf(withdrawal.order()).isPresent()) {
            throw new IllegalStateException("the order already has an active request");
        }
        requests.put(withdrawal.id(), new Stored(withdrawal, Optional.empty()));
        return true;
    }

    @Override
    public synchronized Optional<WithdrawalNotice> noticeOf(UUID requestId) {
        return Optional.ofNullable(requests.get(requestId)).map(Stored::withdrawal).map(withdrawal ->
            new WithdrawalNotice(withdrawal.id(), withdrawal.code().value(), orderOf(withdrawal.order()).number(),
                BUYER_FIRST_NAME, BUYER_LAST_NAME, BUYER_EMAIL, withdrawal.outOfTerm()));
    }

    @Override
    public synchronized List<WithdrawalSummary> page(Optional<WithdrawalStatus> status, int offset, int limit) {
        return requests.values().stream()
            .filter(stored -> status.isEmpty() || stored.status() == status.get())
            .sorted(Comparator.comparing((Stored stored) -> stored.withdrawal().createdAt()).reversed()
                .thenComparing(stored -> stored.withdrawal().code().value()))
            .skip(offset)
            .limit(limit)
            .map(stored -> {
                NewWithdrawal withdrawal = stored.withdrawal();
                WithdrawableOrder order = orderOf(withdrawal.order());
                return new WithdrawalSummary(withdrawal.id(), withdrawal.code(), order.id(), order.number(),
                    order.status(), BUYER_FIRST_NAME + " " + BUYER_LAST_NAME, withdrawal.createdAt(),
                    withdrawal.outOfTerm(), stored.status(), stored.resolvedAt());
            })
            .toList();
    }

    @Override
    public synchronized Optional<ActiveWithdrawal> activeViewOf(OrderId order) {
        return active(order).map(Stored::withdrawal).map(withdrawal -> new ActiveWithdrawal(withdrawal.id(),
            withdrawal.code().value(), withdrawal.outOfTerm(), withdrawal.createdAt()));
    }

    @Override
    public synchronized Optional<LockedWithdrawal> lockForResolution(UUID request) {
        return Optional.ofNullable(requests.get(request)).map(stored -> new LockedWithdrawal(stored.status(),
            stored.withdrawal().createdAt(), stored.resolvedAt()));
    }

    @Override
    public synchronized void resolve(UUID request, Instant at) {
        Stored stored = requests.get(request);
        if (stored == null || stored.resolvedAt().isPresent()) {
            throw new IllegalStateException("withdrawal request " + request + " is not a received one");
        }
        requests.put(request, new Stored(stored.withdrawal(), Optional.of(at)));
    }

    private Optional<Stored> active(OrderId order) {
        return requests.values().stream()
            .filter(stored -> stored.resolvedAt().isEmpty() && stored.withdrawal().order().equals(order))
            .findFirst();
    }

    private WithdrawableOrder orderOf(OrderId id) {
        return orders.values().stream().filter(order -> order.id().equals(id)).findFirst().orElseThrow();
    }
}
