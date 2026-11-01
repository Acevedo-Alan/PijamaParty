package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fake of {@link OpenPayments}: MercadoPago is paid on the store's page (T-E3-29); MODO gets one checkout per order,
 * none while the provider is down. Records the orders.
 */
public class InMemoryOpenPayments implements OpenPayments {

    private final Map<OrderId, URI> checkouts = new ConcurrentHashMap<>();
    private final List<OrderToPay> opened = new CopyOnWriteArrayList<>();
    private final AtomicBoolean down = new AtomicBoolean();

    @Override
    public Opening open(OrderToPay order) {
        opened.add(order);
        if (order.provider() == PaymentProvider.MERCADOPAGO) {
            return new Opening.OnSite();
        }
        if (checkouts.containsKey(order.id())) {
            return new Opening.Redirect(checkouts.get(order.id()));
        }
        if (down.get()) {
            return new Opening.Unavailable();
        }
        return new Opening.Redirect(checkouts.computeIfAbsent(order.id(),
            id -> URI.create("https://pay.example.com/" + order.number())));
    }

    public void down(boolean isDown) {
        down.set(isDown);
    }

    /** Every order whose payment was asked to open, in order. */
    public List<OrderToPay> opened() {
        return List.copyOf(opened);
    }
}
