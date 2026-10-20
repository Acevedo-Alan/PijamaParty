package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-17 (T-E3-09): what the payment-confirmed emails tell about an order. Read in its own transaction, or the
 * caller's, as the caller: the system, when the email job composes them.
 */
public interface PaidOrders {

    /** Empty when there is no such order, or Row Level Security hides it from the actor. */
    Optional<PaidOrder> find(UUID orderId);

    /**
     * The order as the buyer placed it (R-04). Personal data (R-05): {@link #toString()} prints only the number.
     *
     * @param lines in the cart's order
     */
    record PaidOrder(UUID orderId, long number, String buyerFirstName, String buyerLastName, String buyerEmail,
                     List<Line> lines, Money subtotal, Money discount, Optional<String> couponCode, Money shipping,
                     Money total, Address address) {

        public PaidOrder {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(buyerFirstName, "buyerFirstName");
            Objects.requireNonNull(buyerLastName, "buyerLastName");
            Objects.requireNonNull(buyerEmail, "buyerEmail");
            lines = List.copyOf(lines);
            Objects.requireNonNull(subtotal, "subtotal");
            Objects.requireNonNull(discount, "discount");
            Objects.requireNonNull(couponCode, "couponCode");
            Objects.requireNonNull(shipping, "shipping");
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(address, "address");
        }

        @Override
        public String toString() {
            return "PaidOrder[number=" + number + ", ***]";
        }
    }

    record Line(String productName, String size, String color, int quantity, Money unitPrice, Money amount) {

        public Line {
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitPrice, "unitPrice");
            Objects.requireNonNull(amount, "amount");
        }
    }

    /**
     * Personal data (R-05): {@link #toString()} never prints it.
     *
     * @param province the constant of {@code shipping.api.Province}, e.g. {@code SANTA_FE}
     */
    record Address(String street, String number, Optional<String> floor, Optional<String> apartment, String city,
                   String province, String postalCode) {

        public Address {
            Objects.requireNonNull(street, "street");
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(floor, "floor");
            Objects.requireNonNull(apartment, "apartment");
            Objects.requireNonNull(city, "city");
            Objects.requireNonNull(province, "province");
            Objects.requireNonNull(postalCode, "postalCode");
        }

        @Override
        public String toString() {
            return "Address[***]";
        }
    }
}
