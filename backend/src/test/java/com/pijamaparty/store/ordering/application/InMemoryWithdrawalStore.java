package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake of {@link WithdrawableOrders}, {@link ActiveWithdrawals}, {@link SaveWithdrawals} and
 * {@link WithdrawalNoticeDetails}: orders by number, arranged with {@link #place}, and their requests. Like the
 * database, one active request per order and each code once.
 */
public class InMemoryWithdrawalStore
    implements WithdrawableOrders, ActiveWithdrawals, SaveWithdrawals, WithdrawalNoticeDetails {

    public static final String BUYER_NAME = "Ana Pérez";
    public static final String BUYER_EMAIL = "ana@example.com";

    private final Map<Long, WithdrawableOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, Stored> requests = new LinkedHashMap<>();
    private long nextNumber = 1001;

    /** A request as stored; {@code resolvedAt} empty while {@code RECEIVED}. */
    public record Stored(NewWithdrawal withdrawal, Optional<Instant> resolvedAt) {}

    /** An order of {@link #BUYER_NAME} and {@link #BUYER_EMAIL}; answers it with its number. */
    public synchronized WithdrawableOrder place(OrderStatus status, Instant createdAt, Optional<Instant> deliveredAt) {
        var order = new WithdrawableOrder(OrderId.of(UUID.randomUUID()), nextNumber++, BUYER_EMAIL, status, createdAt,
            deliveredAt);
        orders.put(order.number(), order);
        return order;
    }

    public synchronized void resolve(UUID request, Instant at) {
        Stored stored = requests.get(request);
        requests.put(request, new Stored(stored.withdrawal(), Optional.of(at)));
    }

    public synchronized List<Stored> requests() {
        return List.copyOf(requests.values());
    }

    @Override
    public synchronized Optional<WithdrawableOrder> lock(long number) {
        return Optional.ofNullable(orders.get(number));
    }

    @Override
    public synchronized Optional<WithdrawalCode> codeOf(OrderId order) {
        return requests.values().stream()
            .filter(stored -> stored.resolvedAt().isEmpty() && stored.withdrawal().order().equals(order))
            .map(stored -> stored.withdrawal().code())
            .findFirst();
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
        return Optional.ofNullable(requests.get(requestId)).map(Stored::withdrawal).map(withdrawal -> {
            WithdrawableOrder order = orders.values().stream()
                .filter(each -> each.id().equals(withdrawal.order())).findFirst().orElseThrow();
            return new WithdrawalNotice(withdrawal.id(), withdrawal.code().value(), order.number(), BUYER_NAME,
                order.email(), withdrawal.outOfTerm());
        });
    }
}
