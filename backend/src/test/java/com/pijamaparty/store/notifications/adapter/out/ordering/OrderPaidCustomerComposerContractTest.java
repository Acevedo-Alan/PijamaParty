package com.pijamaparty.store.notifications.adapter.out.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.application.EmailComposerContractTest;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** UC-17: the buyer's payment-confirmed email, from the order as {@code ordering.api} tells it. */
class OrderPaidCustomerComposerContractTest extends EmailComposerContractTest<OrderPaidCustomerComposer> {

    private final InMemoryPaidOrders orders = new InMemoryPaidOrders();

    static ResourceBundleMessageSource messages() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    @Override
    protected OrderPaidCustomerComposer createSubject() {
        return new OrderPaidCustomerComposer(orders, orders, messages(), URI.create("https://pijamaparty.example/"));
    }

    @Override
    protected EmailType expectedType() {
        return EmailType.ORDER_PAID_CUSTOMER;
    }

    @Override
    protected UUID existingReference() {
        return orders.paid().orderId();
    }

    @Test
    void givenAPaidOrder_whenComposed_thenItGoesToTheBuyerWithTheOrderNumberInTheSubject() {
        EmailMessage message = compose(orders.paid());

        assertThat(message.to()).isEqualTo("ana@example.com");
        assertThat(message.subject()).isEqualTo("Recibimos tu pago - Pedido #1001");
    }

    @Test
    void givenAPaidOrder_whenComposed_thenBothBodiesGreetTheBuyerAndHaveTheSummaryTotalAndAddress() {
        EmailMessage message = compose(orders.paid());

        for (String body : new String[] {message.text(), message.html()}) {
            assertThat(body).contains("¡Hola, Ana!", "pedido #1001",
                "Pijama Luna - Talle M - Rosa x 2", "$ 40.000,00", "Talle S - Gris x 1", "$ 10.000,00",
                "Subtotal", "$ 50.000,00", "Descuento (VERANO10)", "- $ 5.000,00", "Envío", "$ 3.000,00",
                "Total", "$ 48.000,00",
                "San Martín 1234, piso 3, depto. B", "Santa Fe, Santa Fe", "CP S3000ABC",
                "número de seguimiento del envío", "respondé este correo");
        }
    }

    @Test
    void givenAPaidOrder_whenComposed_thenItCarriesANewTrackingLinkForThatOrder() {
        PaidOrder order = orders.paid();

        EmailMessage message = compose(order);

        assertThat(orders.linked()).containsExactly(order.orderId());
        assertThat(message.text()).contains("https://pijamaparty.example/pedido/link-token-1");
        assertThat(message.html()).contains("href=\"https://pijamaparty.example/pedido/link-token-1\"");
    }

    @Test
    void givenTheSameOrderComposedTwice_whenComposed_thenEachEmailHasItsOwnLink() {
        PaidOrder order = orders.paid();

        assertThat(compose(order).text()).contains("/pedido/link-token-1");
        assertThat(compose(order).text()).contains("/pedido/link-token-2");
    }

    @Test
    void givenAnOrderWithoutCouponAndWithFreeShipping_whenComposed_thenThereIsNoDiscountAndShippingIsFree() {
        EmailMessage message = compose(orders.paid(Money.zero(), Optional.empty(), Money.zero()));

        assertThat(message.text()).doesNotContain("Descuento").contains("Envío: Gratis", "Total: $ 50.000,00");
    }

    @Test
    void givenWhatTheBuyerTyped_whenComposed_thenTheHtmlEscapesIt() {
        EmailMessage message = compose(orders.paid());

        assertThat(message.html()).contains("Camisón &lt;Sol&gt;").doesNotContain("<Sol>");
        assertThat(message.text()).contains("Camisón <Sol>");
    }

    @Test
    void givenNoSuchOrder_whenComposed_thenNoLinkIsIssued() {
        subject.compose(UUID.randomUUID());

        assertThat(orders.linked()).isEmpty();
    }

    private EmailMessage compose(PaidOrder order) {
        return ((Composition.Composed) subject.compose(order.orderId())).message();
    }
}
