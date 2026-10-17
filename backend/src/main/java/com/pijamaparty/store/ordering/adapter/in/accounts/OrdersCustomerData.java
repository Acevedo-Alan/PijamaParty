package com.pijamaparty.store.ordering.adapter.in.accounts;

import com.pijamaparty.store.accounts.api.CustomerDataContributor;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.ordering.application.ExportCustomerOrders;
import com.pijamaparty.store.ordering.application.ExportedOrder;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UC-28 (T-E4-16): the orders section of the customer's data download, which {@code accounts} collects. Only
 * translates the use case's orders into records ready to be written as JSON, with the shapes of "Mis pedidos" (UC-22)
 * plus the buyer's copy and the delivery date.
 */
@Component
class OrdersCustomerData implements CustomerDataContributor {

    private final ExportCustomerOrders export;

    OrdersCustomerData(ExportCustomerOrders export) {
        this.export = export;
    }

    @Override
    public CustomerDataSection section() {
        return CustomerDataSection.ORDERS;
    }

    @Override
    public List<OrderRecord> export(UserId customer) {
        return export.execute(new ExportCustomerOrders.Query(customer)).stream().map(OrderRecord::of).toList();
    }

    /** {@code totalCents} = {@code subtotalCents} − {@code discountCents} + {@code shippingCents}. */
    record OrderRecord(UUID id, long number, OrderStatus status, Instant createdAt, Instant deliveredAt,
                       BuyerRecord buyer, AddressRecord address, List<ItemRecord> items, long subtotalCents,
                       long discountCents, String couponCode, long shippingCents, long totalCents,
                       String paymentProvider, String shipmentTrackingNumber) {

        static OrderRecord of(ExportedOrder order) {
            var amounts = order.amounts();
            var address = order.address();
            var buyer = order.buyer();
            return new OrderRecord(order.id().value(), order.number(), order.status(), order.createdAt(),
                order.deliveredAt().orElse(null),
                new BuyerRecord(buyer.name(), buyer.email(), buyer.phone(), buyer.nationalId()),
                new AddressRecord(address.street(), address.number(), address.floor().orElse(null),
                    address.apartment().orElse(null), address.city(), order.province(), address.postalCode()),
                order.lines().stream().map(ItemRecord::of).toList(), amounts.subtotal().cents(),
                amounts.discount().cents(), order.couponCode().orElse(null), amounts.shipping().cents(),
                amounts.total().cents(), order.paymentProvider(), order.shipmentTrackingNumber().orElse(null));
        }

        @Override
        public String toString() {
            return "OrderRecord[number=" + number + ", status=" + status + ", ***]";
        }
    }

    record BuyerRecord(String name, String email, String phone, String nationalId) {

        @Override
        public String toString() {
            return "BuyerRecord[***]";
        }
    }

    record AddressRecord(String street, String number, String floor, String apartment, String city,
                         Province province, String postalCode) {

        @Override
        public String toString() {
            return "AddressRecord[***]";
        }
    }

    /** {@code lineTotalCents} = {@code unitPriceCents} × {@code quantity}. */
    record ItemRecord(String sku, String productName, String size, String color, int quantity, long unitPriceCents,
                      long lineTotalCents) {

        static ItemRecord of(OrderLine line) {
            return new ItemRecord(line.sku(), line.productName(), line.size(), line.color(), line.quantity(),
                line.unitPrice().cents(), line.amount().cents());
        }
    }
}
