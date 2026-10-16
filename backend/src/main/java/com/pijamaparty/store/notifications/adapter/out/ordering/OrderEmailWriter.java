package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.notifications.adapter.out.mail.EmailWriter;
import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.platform.domain.Money;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;

/** An email about an order: the parts of every email, plus the order's summary, amounts and address. */
final class OrderEmailWriter extends EmailWriter<OrderEmailWriter> {

    OrderEmailWriter(MessageSource messages) {
        super(messages);
    }

    @Override
    protected OrderEmailWriter self() {
        return this;
    }

    /** {@code $ 44.500,00}: grouped pesos and two decimals, as Argentina writes them, straight from the cents. */
    static String pesos(Money amount) {
        var symbols = new DecimalFormatSymbols(CUSTOMER_LOCALE);
        symbols.setGroupingSeparator('.');
        return "$ " + new DecimalFormat("#,##0", symbols).format(amount.cents() / 100) + ","
            + String.format(Locale.ROOT, "%02d", amount.cents() % 100);
    }

    /** @param province the constant of {@code shipping.api.Province}, as {@code ordering.api} gives it */
    String province(String province) {
        return message("province." + province);
    }

    /** The lines with their totals, then subtotal, discount (only with a coupon), shipping and total. */
    OrderEmailWriter summary(PaidOrder order) {
        appendHtml("<table role=\"presentation\" style=\"border-collapse:collapse;width:100%;margin:0 0 16px\">\n");
        for (Line line : order.lines()) {
            row(message("notifications.order.line", line.productName(), line.size(), line.color(),
                Integer.toString(line.quantity())), pesos(line.amount()), false);
        }
        row(message("notifications.order.subtotal"), pesos(order.subtotal()), false);
        if (!order.discount().isZero()) {
            row(message("notifications.order.discount", order.couponCode().orElse("")),
                "- " + pesos(order.discount()), false);
        }
        row(message("notifications.order.shipping"), order.shipping().isZero()
            ? message("notifications.order.free-shipping")
            : pesos(order.shipping()), false);
        row(message("notifications.order.total"), pesos(order.total()), true);
        appendHtml("</table>\n");
        appendText("\n");
        return this;
    }

    /** Street and number, floor and apartment when there are, then city, province and postal code. */
    List<String> address(Address address) {
        List<String> first = new ArrayList<>();
        first.add(address.street() + " " + address.number());
        address.floor().ifPresent(floor -> first.add(message("notifications.order.floor", floor)));
        address.apartment().ifPresent(apartment -> first.add(message("notifications.order.apartment", apartment)));
        return List.of(String.join(", ", first),
            address.city() + ", " + province(address.province()),
            message("notifications.order.postal-code", address.postalCode()));
    }

    private void row(String label, String amount, boolean strong) {
        String weight = strong ? "font-weight:bold;" : "";
        appendHtml("<tr><td style=\"" + weight + "padding:4px 0\">" + escape(label)
            + "</td><td style=\"" + weight + "padding:4px 0;text-align:right;white-space:nowrap\">"
            + escape(amount) + "</td></tr>\n");
        appendText(label + ": " + amount + "\n");
    }
}
