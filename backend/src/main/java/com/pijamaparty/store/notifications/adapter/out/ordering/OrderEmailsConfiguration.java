package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.ordering.api.PaidOrders;
import com.pijamaparty.store.ordering.api.TrackingLinks;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The composers of the emails about an order (UC-17).
 *
 * <p>{@code store.public-url}: the storefront's origin, where the tracking link points. {@code
 * store.notifications.admin-recipient}: the administrator's address ({@code NOTIFICATIONS_ADMIN_EMAIL}), read with
 * {@code @Value}, so an unresolved placeholder stops the start-up (the production profile has no default).
 */
@Configuration(proxyBeanMethods = false)
class OrderEmailsConfiguration {

    @Bean
    OrderPaidCustomerComposer orderPaidCustomerComposer(PaidOrders orders, TrackingLinks trackingLinks,
                                                        MessageSource messages,
                                                        @Value("${store.public-url}") URI publicUrl) {
        return new OrderPaidCustomerComposer(orders, trackingLinks, messages, publicUrl);
    }

    @Bean
    OrderPaidAdminComposer orderPaidAdminComposer(
        PaidOrders orders, MessageSource messages, @Value("${store.notifications.admin-recipient}") String recipient) {
        return new OrderPaidAdminComposer(orders, messages, recipient);
    }
}
