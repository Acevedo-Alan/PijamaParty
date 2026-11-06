package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.PaymentProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake of {@link CardCharges}: MercadoPago approving every card unless told otherwise, one payment per submit's key.
 * Records the charges.
 */
public class InMemoryCardCharges implements CardCharges {

    private enum Mode { APPROVE, REJECT_FOR_FUNDS, REVIEW, REFUSE, DOWN }

    private final Map<UUID, Charge> bySubmit = new ConcurrentHashMap<>();
    private final List<CardCharge> charges = new CopyOnWriteArrayList<>();
    private final AtomicInteger ids = new AtomicInteger(1_000_000);
    private final Optional<String> publicKey;
    private volatile Mode mode = Mode.APPROVE;

    public InMemoryCardCharges() {
        this(Optional.of("TEST-public-key"));
    }

    public InMemoryCardCharges(Optional<String> publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    public Optional<String> publicKey() {
        return publicKey;
    }

    @Override
    public Charge charge(CardCharge charge) {
        charges.add(charge);
        return switch (mode) {
            case DOWN -> new Charge.Unavailable();
            case REFUSE -> new Charge.Refused();
            case APPROVE -> bySubmit.computeIfAbsent(charge.idempotencyKey(),
                key -> charged(ChargeStatus.APPROVED, Optional.empty()));
            case REJECT_FOR_FUNDS -> bySubmit.computeIfAbsent(charge.idempotencyKey(),
                key -> charged(ChargeStatus.REJECTED, Optional.of(CardRejection.INSUFFICIENT_FUNDS)));
            case REVIEW -> bySubmit.computeIfAbsent(charge.idempotencyKey(),
                key -> charged(ChargeStatus.IN_PROCESS, Optional.empty()));
        };
    }

    private Charge charged(ChargeStatus status, Optional<CardRejection> rejection) {
        return new Charge.Charged(Integer.toString(ids.incrementAndGet()), status, rejection);
    }

    public void approve() {
        mode = Mode.APPROVE;
    }

    public void rejectForFunds() {
        mode = Mode.REJECT_FOR_FUNDS;
    }

    public void review() {
        mode = Mode.REVIEW;
    }

    public void refuse() {
        mode = Mode.REFUSE;
    }

    public void down() {
        mode = Mode.DOWN;
    }

    /** Every charge asked, in order. */
    public List<CardCharge> charges() {
        return List.copyOf(charges);
    }
}
