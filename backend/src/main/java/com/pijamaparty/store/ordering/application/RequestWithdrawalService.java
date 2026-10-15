package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.domain.BuyerEmail;
import com.pijamaparty.store.ordering.domain.OrderNumberText;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.ordering.domain.WithdrawalEligibility;
import com.pijamaparty.store.ordering.domain.WithdrawalTerm;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-29 (T-E3-25). One transaction, as the system: the order stays locked from comparing its email until the request
 * and the emails the listeners of {@code WithdrawalRequested} enqueue commit together.
 */
@Service
class RequestWithdrawalService implements RequestWithdrawal {

    /** 31^8 codes: one collision is already rare; five in a row is a fault, not chance. */
    static final int CODE_ATTEMPTS = 5;

    private final WithdrawableOrders orders;
    private final ActiveWithdrawals active;
    private final SaveWithdrawals withdrawals;
    private final WithdrawalCodes codes;
    private final OrderEvents events;
    private final IdGenerator ids;
    private final Clock clock;

    RequestWithdrawalService(WithdrawableOrders orders, ActiveWithdrawals active, SaveWithdrawals withdrawals,
                             WithdrawalCodes codes, OrderEvents events, IdGenerator ids, Clock clock) {
        this.orders = orders;
        this.active = active;
        this.withdrawals = withdrawals;
        this.codes = codes;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        OptionalLong number = OrderNumberText.parse(command.orderNumber());
        Optional<String> email = BuyerEmail.normalize(command.email());
        if (number.isEmpty() || email.isEmpty()) {
            return new Result.NoMatchingOrder();
        }
        Optional<WithdrawableOrder> found = orders.lock(number.getAsLong());
        if (found.isEmpty() || !found.get().email().equals(email.get())) {
            return new Result.NoMatchingOrder();
        }
        WithdrawableOrder order = found.get();
        if (!WithdrawalEligibility.allows(order.status())) {
            return new Result.NotWithdrawable(order.status());
        }
        Optional<WithdrawalCode> activeCode = active.codeOf(order.id());
        if (activeCode.isPresent()) {
            return new Result.AlreadyRequested(activeCode.get());
        }
        Instant now = clock.instant();
        boolean outOfTerm = WithdrawalTerm.isOutOfTerm(order.createdAt(), order.deliveredAt(), now);
        UUID id = ids.newId();
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            WithdrawalCode code = codes.next();
            if (withdrawals.insert(new NewWithdrawal(id, order.id(), code, outOfTerm, now))) {
                events.withdrawalRequested(id, order.id(), order.number());
                return new Result.Requested(code);
            }
        }
        throw new IllegalStateException(CODE_ATTEMPTS + " withdrawal codes in a row were already taken");
    }
}
