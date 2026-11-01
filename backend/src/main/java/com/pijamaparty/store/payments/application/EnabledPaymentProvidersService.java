package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.api.PaymentProviders;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** UC-14 (T-E3b-02): the providers whose gateway is switched on, and the card form's key (T-E3-29). */
@Service
class EnabledPaymentProvidersService implements PaymentProviders {

    private final Set<PaymentProvider> enabled;
    private final Optional<String> cardFormPublicKey;

    EnabledPaymentProvidersService(List<PaymentGateway> gateways, List<CardCharges> cardCharges) {
        Set<PaymentProvider> providers = EnumSet.noneOf(PaymentProvider.class);
        for (CardCharges charges : cardCharges) {
            if (charges.enabled()) {
                providers.add(charges.provider());
            }
        }
        for (PaymentGateway gateway : gateways) {
            if (gateway.enabled()) {
                providers.add(gateway.provider());
            }
        }
        this.enabled = Collections.unmodifiableSet(providers);
        this.cardFormPublicKey = cardCharges.stream().map(CardCharges::publicKey).flatMap(Optional::stream).findFirst();
    }

    @Override
    public Set<PaymentProvider> enabled() {
        return enabled;
    }

    @Override
    public Optional<String> cardFormPublicKey() {
        return cardFormPublicKey;
    }
}
