package com.pijamaparty.store.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailMessageTest {

    @Test
    void givenAMessage_whenPrinted_thenTheRecipientAndTheContentAreHidden() {
        var message = new EmailMessage("ana@example.com", "Recibimos tu pago - Pedido #1001", "<p>San Martín 1234</p>",
            "San Martín 1234");

        assertThat(message.toString()).contains("Pedido #1001").doesNotContain("ana@example.com", "San Martín");
    }

    @Test
    void givenABlankPart_whenCreated_thenItFails() {
        assertThatThrownBy(() -> new EmailMessage(" ", "subject", "html", "text"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailMessage("ana@example.com", "subject", "", "text"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailMessage("ana@example.com", null, "html", "text"))
            .isInstanceOf(NullPointerException.class);
    }
}
