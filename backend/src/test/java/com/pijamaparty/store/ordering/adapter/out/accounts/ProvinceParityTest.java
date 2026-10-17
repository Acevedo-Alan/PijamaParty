package com.pijamaparty.store.ordering.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.shipping.api.Province;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * UC-21: {@link AccountsModuleCheckoutDetails} hands the order's province to the address book by its constant, so
 * every province of {@code shipping.api} must be one of {@code accounts.domain.Province} too.
 */
class ProvinceParityTest {

    @Test
    void givenTheProvincesOfShipping_thenTheAddressBookHasTheSameConstants() {
        assertThat(Arrays.stream(com.pijamaparty.store.accounts.domain.Province.values()).map(Enum::name))
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(Province.values()).map(Enum::name).toList());
    }
}
