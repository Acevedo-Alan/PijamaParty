package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link PurchasedProducts} (UC-24): who bought a product, given its variants. */
public abstract class PurchasedProductsContractTest<T extends PurchasedProducts> extends ContractTest<T> {

    /** The order statuses in which the buyer bought its items. */
    protected static final List<String> BOUGHT = List.of("PAID", "PREPARING", "SHIPPED", "DELIVERED");

    /** An order of {@code customer}, in {@code status} (an order status name), with one line of {@code variant}. */
    protected abstract void givenOrder(UserId customer, String status, UUID variant);

    @Test
    void givenAnOrderInEveryBoughtStatus_whenAskingWhetherItsBuyerBoughtTheVariant_thenItDid() {
        for (String status : BOUGHT) {
            UserId ana = UserId.of(UUID.randomUUID());
            UUID variant = UUID.randomUUID();
            givenOrder(ana, status, variant);

            assertThat(subject.boughtAnyOf(ana, Set.of(UUID.randomUUID(), variant))).as(status).isTrue();
        }
    }

    @Test
    void givenAnOrderNotPaidCancelledOrExpired_whenAskingWhetherItsBuyerBoughtTheVariant_thenItDidNot() {
        for (String status : List.of("AWAITING_PAYMENT", "CANCELLED", "EXPIRED")) {
            UserId ana = UserId.of(UUID.randomUUID());
            UUID variant = UUID.randomUUID();
            givenOrder(ana, status, variant);

            assertThat(subject.boughtAnyOf(ana, Set.of(variant))).as(status).isFalse();
        }
    }

    @Test
    void givenAPaidOrderOfAnotherVariantOrAnotherCustomer_whenAsking_thenTheCustomerDidNotBuyIt() {
        UserId ana = UserId.of(UUID.randomUUID());
        UserId bruno = UserId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenOrder(ana, "PAID", UUID.randomUUID());
        givenOrder(bruno, "PAID", variant);

        assertThat(subject.boughtAnyOf(ana, Set.of(variant))).isFalse();
    }

    @Test
    void givenNoVariants_whenAsking_thenTheCustomerBoughtNone() {
        UserId ana = UserId.of(UUID.randomUUID());
        givenOrder(ana, "PAID", UUID.randomUUID());

        assertThat(subject.boughtAnyOf(ana, Set.of())).isFalse();
    }
}
