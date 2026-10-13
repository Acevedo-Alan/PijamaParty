package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link EmailComposer} (Open/Closed, Liskov Substitution): one type of email; a reference that exists
 * gives a complete email, one that does not gives {@code Gone}, never an exception.
 */
public abstract class EmailComposerContractTest<T extends EmailComposer> extends ContractTest<T> {

    protected abstract EmailType expectedType();

    /** Arranges what the email tells about, and answers its reference. */
    protected abstract UUID existingReference();

    @Test
    void givenTheComposer_whenAskedForItsType_thenItIsTheOneItWrites() {
        assertThat(subject.type()).isEqualTo(expectedType());
    }

    @Test
    void givenAReferenceThatExists_whenComposed_thenTheEmailHasARecipientASubjectAndBothBodies() {
        UUID reference = existingReference();

        assertThat(subject.compose(reference)).isInstanceOfSatisfying(Composition.Composed.class, composed -> {
            assertThat(composed.message().to()).contains("@");
            assertThat(composed.message().subject()).isNotBlank();
            assertThat(composed.message().html()).isNotBlank();
            assertThat(composed.message().text()).isNotBlank();
        });
    }

    @Test
    void givenAReferenceThatDoesNotExist_whenComposed_thenItIsGone() {
        assertThat(subject.compose(UUID.randomUUID())).isInstanceOf(Composition.Gone.class);
    }
}
