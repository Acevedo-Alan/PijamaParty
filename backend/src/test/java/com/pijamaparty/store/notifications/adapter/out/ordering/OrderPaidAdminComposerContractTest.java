package com.pijamaparty.store.notifications.adapter.out.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.application.EmailComposerContractTest;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** UC-17: the administrator's notice of a paid order. */
@ExtendWith(OutputCaptureExtension.class)
class OrderPaidAdminComposerContractTest extends EmailComposerContractTest<OrderPaidAdminComposer> {

    private final InMemoryPaidOrders orders = new InMemoryPaidOrders();

    @Override
    protected OrderPaidAdminComposer createSubject() {
        return new OrderPaidAdminComposer(orders, OrderPaidCustomerComposerContractTest.messages(),
            "admin@pijamaparty.example");
    }

    @Override
    protected EmailType expectedType() {
        return EmailType.ORDER_PAID_ADMIN;
    }

    @Override
    protected UUID existingReference() {
        return orders.paid().orderId();
    }

    @Test
    void givenAPaidOrder_whenComposed_thenTheAdministratorGetsTheBuyerTheDestinationTheLinesAndTheTotal() {
        UUID order = orders.paid().orderId();

        EmailMessage message = ((Composition.Composed) subject.compose(order)).message();

        assertThat(message.to()).isEqualTo("admin@pijamaparty.example");
        assertThat(message.subject()).isEqualTo("Nuevo pedido pagado #1001");
        for (String body : new String[] {message.text(), message.html()}) {
            assertThat(body).contains("María José Pérez", "Santa Fe, Santa Fe", "Pijama Luna - Talle M - Rosa x 2",
                "Total", "$ 48.000,00", "backoffice");
            assertThat(body).doesNotContain("San Martín", "ana@example.com", "/pedido/");
        }
        assertThat(orders.linked()).isEmpty();
    }

    @Test
    void givenNoAdministratorConfigured_whenComposed_thenItIsGoneWithAWarning(CapturedOutput output) {
        UUID order = orders.paid().orderId();
        var unconfigured = new OrderPaidAdminComposer(orders, OrderPaidCustomerComposerContractTest.messages(), " ");

        assertThat(unconfigured.compose(order)).isInstanceOf(Composition.Gone.class);
        assertThat(output.getAll()).contains("No administrator to tell about paid order " + order);
    }
}
