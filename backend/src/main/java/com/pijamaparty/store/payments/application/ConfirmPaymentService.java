package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * UC-15. Deliberately not transactional: the provider is asked first, with no database connection held (RNF-21), and
 * then {@link VerifiedPaymentRecorder} records the answer in one short transaction.
 */
@Service
class ConfirmPaymentService implements ConfirmPayment {

    private final Map<PaymentProvider, PaymentVerifier> verifiers;
    private final VerifiedPaymentRecorder recorder;

    ConfirmPaymentService(List<PaymentVerifier> verifiers, VerifiedPaymentRecorder recorder) {
        this.verifiers = new EnumMap<>(PaymentProvider.class);
        for (PaymentVerifier verifier : verifiers) {
            if (this.verifiers.put(verifier.provider(), verifier) != null) {
                throw new IllegalStateException("two payment verifiers for " + verifier.provider());
            }
        }
        this.recorder = recorder;
    }

    @Override
    public Result execute(Command command) {
        PaymentVerifier verifier = verifiers.get(command.provider());
        if (verifier == null) {
            throw new IllegalStateException("no payment verifier for " + command.provider());
        }
        return switch (verifier.verify(command.externalPaymentId())) {
            case PaymentVerifier.Verification.Unavailable unavailable -> new Result.ProviderUnavailable();
            case PaymentVerifier.Verification.NotFound notFound -> new Result.NotFoundAtProvider();
            case PaymentVerifier.Verification.Found found -> recorder.record(command.provider(), found.payment());
        };
    }
}
