package com.pijamaparty.store.notifications.adapter.out.ordering;

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
import org.springframework.web.util.HtmlUtils;

/**
 * Writes the body of an email about an order twice, as HTML and as plain text, with the texts of
 * {@code messages.properties} in Spanish (Argentina). Everything that comes from the order is escaped in the HTML.
 * Not thread-safe: one writer per email.
 */
final class OrderEmailWriter {

    static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");

    private final MessageSource messages;
    private final StringBuilder html = new StringBuilder();
    private final StringBuilder text = new StringBuilder();

    OrderEmailWriter(MessageSource messages) {
        this.messages = messages;
    }

    /** {@code $ 44.500,00}: grouped pesos and two decimals, as Argentina writes them, straight from the cents. */
    static String pesos(Money amount) {
        var symbols = new DecimalFormatSymbols(CUSTOMER_LOCALE);
        symbols.setGroupingSeparator('.');
        return "$ " + new DecimalFormat("#,##0", symbols).format(amount.cents() / 100) + ","
            + String.format(Locale.ROOT, "%02d", amount.cents() % 100);
    }

    /** The first word of the name, for the greeting. */
    static String firstName(String name) {
        return name.strip().split("\\s+", 2)[0];
    }

    String message(String key, Object... arguments) {
        return messages.getMessage(key, arguments, CUSTOMER_LOCALE);
    }

    /** @param province the constant of {@code shipping.api.Province}, as {@code ordering.api} gives it */
    String province(String province) {
        return message("province." + province);
    }

    OrderEmailWriter heading(String content) {
        html.append("<h1 style=\"font-size:20px;margin:0 0 16px\">").append(escape(content)).append("</h1>\n");
        text.append(content).append("\n\n");
        return this;
    }

    OrderEmailWriter paragraph(String content) {
        html.append("<p style=\"margin:0 0 16px\">").append(escape(content)).append("</p>\n");
        text.append(content).append("\n\n");
        return this;
    }

    /** A label and the lines that follow it, kept together. */
    OrderEmailWriter block(String label, List<String> lines) {
        html.append("<p style=\"margin:0 0 16px\"><strong>").append(escape(label)).append("</strong><br>");
        html.append(String.join("<br>", lines.stream().map(OrderEmailWriter::escape).toList())).append("</p>\n");
        text.append(label).append('\n').append(String.join("\n", lines)).append("\n\n");
        return this;
    }

    /** A button in the HTML; in the text, the explanation and then the bare address. */
    OrderEmailWriter link(String explanation, String button, String url) {
        html.append("<p style=\"margin:0 0 8px\">").append(escape(explanation)).append("</p>\n")
            .append("<p style=\"margin:0 0 16px\"><a href=\"").append(escape(url))
            .append("\" style=\"display:inline-block;padding:10px 16px;background:#6b4fa0;color:#ffffff;")
            .append("text-decoration:none;border-radius:6px\">").append(escape(button)).append("</a></p>\n");
        text.append(explanation).append('\n').append(url).append("\n\n");
        return this;
    }

    /** The lines with their totals, then subtotal, discount (only with a coupon), shipping and total. */
    OrderEmailWriter summary(PaidOrder order) {
        html.append("<table role=\"presentation\" style=\"border-collapse:collapse;width:100%;margin:0 0 16px\">\n");
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
        html.append("</table>\n");
        text.append('\n');
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

    String html() {
        return "<!DOCTYPE html>\n<html lang=\"es\">\n<body style=\"font-family:Arial,Helvetica,sans-serif;"
            + "font-size:15px;line-height:1.5;color:#222222;margin:0;padding:24px\">\n"
            + "<div style=\"max-width:560px;margin:0 auto\">\n" + html + "</div>\n</body>\n</html>\n";
    }

    String text() {
        return text.toString().strip() + "\n";
    }

    private void row(String label, String amount, boolean strong) {
        String weight = strong ? "font-weight:bold;" : "";
        html.append("<tr><td style=\"").append(weight).append("padding:4px 0\">").append(escape(label))
            .append("</td><td style=\"").append(weight).append("padding:4px 0;text-align:right;white-space:nowrap\">")
            .append(escape(amount)).append("</td></tr>\n");
        text.append(label).append(": ").append(amount).append('\n');
    }

    private static String escape(String content) {
        return HtmlUtils.htmlEscape(content, "UTF-8");
    }
}
