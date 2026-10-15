package com.pijamaparty.store.notifications.adapter.out.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.application.EmailComposerContractTest;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** UC-29 (T-E3-25): the procedure code to the buyer and the notice to the administrator. */
class WithdrawalComposersContractTest {

    @Nested
    class ToTheBuyer extends EmailComposerContractTest<WithdrawalCustomerComposer> {

        private final InMemoryWithdrawalNotices notices = new InMemoryWithdrawalNotices();

        @Override
        protected WithdrawalCustomerComposer createSubject() {
            return new WithdrawalCustomerComposer(notices, OrderPaidCustomerComposerContractTest.messages());
        }

        @Override
        protected EmailType expectedType() {
            return EmailType.WITHDRAWAL_RECEIVED_CUSTOMER;
        }

        @Override
        protected UUID existingReference() {
            return notices.requested(false).requestId();
        }

        @Test
        void givenAWithdrawalRequest_whenComposed_thenTheBuyerGetsItsCodeAndWhatComesNext() {
            UUID request = notices.requested(true).requestId();

            EmailMessage message = ((Composition.Composed) subject.compose(request)).message();

            assertThat(message.to()).isEqualTo("ana@example.com");
            assertThat(message.subject()).isEqualTo("Recibimos tu solicitud de arrepentimiento - Pedido #1001");
            for (String body : new String[] {message.text(), message.html()}) {
                assertThat(body).contains("¡Hola, Ana!", "pedido #1001", "ARR-7K2M9QXZ", "no tiene costo");
                assertThat(body).doesNotContain("fuera del plazo");
            }
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class ToTheAdministrator extends EmailComposerContractTest<WithdrawalAdminComposer> {

        private final InMemoryWithdrawalNotices notices = new InMemoryWithdrawalNotices();

        @Override
        protected WithdrawalAdminComposer createSubject() {
            return new WithdrawalAdminComposer(notices, OrderPaidCustomerComposerContractTest.messages(),
                "admin@pijamaparty.example");
        }

        @Override
        protected EmailType expectedType() {
            return EmailType.WITHDRAWAL_RECEIVED_ADMIN;
        }

        @Override
        protected UUID existingReference() {
            return notices.requested(false).requestId();
        }

        @Test
        void givenARequestInTerm_whenComposed_thenTheAdministratorGetsTheOrderTheCodeAndWhatToDo() {
            UUID request = notices.requested(false).requestId();

            EmailMessage message = ((Composition.Composed) subject.compose(request)).message();

            assertThat(message.to()).isEqualTo("admin@pijamaparty.example");
            assertThat(message.subject()).isEqualTo("Solicitud de arrepentimiento ARR-7K2M9QXZ - Pedido #1001");
            for (String body : new String[] {message.text(), message.html()}) {
                assertThat(body).contains("#1001", "ARR-7K2M9QXZ", "reintegrá el pago");
                assertThat(body).doesNotContain("fuera del plazo", "ana@example.com");
            }
        }

        @Test
        void givenARequestOutOfTerm_whenComposed_thenTheNoticeSaysSo() {
            UUID request = notices.requested(true).requestId();

            EmailMessage message = ((Composition.Composed) subject.compose(request)).message();

            assertThat(message.text()).contains("fuera del plazo de 10 días");
        }

        @Test
        void givenNoAdministratorConfigured_whenComposed_thenItIsGoneWithAWarning(CapturedOutput output) {
            UUID request = notices.requested(false).requestId();
            var unconfigured = new WithdrawalAdminComposer(notices, OrderPaidCustomerComposerContractTest.messages(),
                " ");

            assertThat(unconfigured.compose(request)).isInstanceOf(Composition.Gone.class);
            assertThat(output.getAll()).contains("No administrator to tell about withdrawal request " + request);
        }
    }
}
