package com.pijamaparty.store.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TypedIdTest {

    @Test
    void givenTheSameUuid_whenCreatingTwoUserIds_thenTheyAreEqual() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(UserId.of(uuid)).isEqualTo(UserId.of(uuid));
        assertThat(UserId.of(uuid)).hasToString(uuid.toString());
    }

    @Test
    void givenNoUuid_whenCreatingAUserId_thenItIsRejected() {
        assertThatThrownBy(() -> UserId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenAUseCaseThatCreatesIds_whenItIsTested_thenTheTestControlsTheIdsThroughTheInjectedGenerator() {
        UUID fixed = UUID.fromString("00000000-0000-0000-0000-00000000002a");
        IdGenerator ids = () -> fixed;

        assertThat(UserId.of(ids.newId())).isEqualTo(UserId.of(fixed));
    }

    @Test
    void givenABlankToken_whenCreatingIt_thenItIsRejected() {
        assertThatThrownBy(() -> new CartToken(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
