package com.pijamaparty.store.notifications.adapter.out.mail;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.web.util.HtmlUtils;

/**
 * Writes the body of an email twice, as HTML and as plain text, with the texts of {@code messages.properties} in
 * Spanish (Argentina). Everything given to it is escaped in the HTML. Each kind of email adds its own parts in a
 * subclass. Not thread-safe: one writer per email.
 *
 * @param <W> the subclass, so the calls chain through its own parts too
 */
public abstract class EmailWriter<W extends EmailWriter<W>> {

    public static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");

    private final MessageSource messages;
    private final StringBuilder html = new StringBuilder();
    private final StringBuilder text = new StringBuilder();

    protected EmailWriter(MessageSource messages) {
        this.messages = messages;
    }

    protected abstract W self();

    /** The first word of the name, for the greeting. */
    public static String firstName(String name) {
        return name.strip().split("\s+", 2)[0];
    }

    public String message(String key, Object... arguments) {
        return messages.getMessage(key, arguments, CUSTOMER_LOCALE);
    }

    public W heading(String content) {
        html.append("<h1 style=\"font-size:20px;margin:0 0 16px\">").append(escape(content)).append("</h1>\n");
        text.append(content).append("\n\n");
        return self();
    }

    public W paragraph(String content) {
        html.append("<p style=\"margin:0 0 16px\">").append(escape(content)).append("</p>\n");
        text.append(content).append("\n\n");
        return self();
    }

    /** A label and the lines that follow it, kept together. */
    public W block(String label, List<String> lines) {
        html.append("<p style=\"margin:0 0 16px\"><strong>").append(escape(label)).append("</strong><br>");
        html.append(String.join("<br>", lines.stream().map(EmailWriter::escape).toList())).append("</p>\n");
        text.append(label).append('\n').append(String.join("\n", lines)).append("\n\n");
        return self();
    }

    /** A button in the HTML; in the text, the explanation and then the bare address. */
    public W link(String explanation, String button, String url) {
        html.append("<p style=\"margin:0 0 8px\">").append(escape(explanation)).append("</p>\n")
            .append("<p style=\"margin:0 0 16px\"><a href=\"").append(escape(url))
            .append("\" style=\"display:inline-block;padding:10px 16px;background:#6b4fa0;color:#ffffff;")
            .append("text-decoration:none;border-radius:6px\">").append(escape(button)).append("</a></p>\n");
        text.append(explanation).append('\n').append(url).append("\n\n");
        return self();
    }

    public String html() {
        return "<!DOCTYPE html>\n<html lang=\"es\">\n<body style=\"font-family:Arial,Helvetica,sans-serif;"
            + "font-size:15px;line-height:1.5;color:#222222;margin:0;padding:24px\">\n"
            + "<div style=\"max-width:560px;margin:0 auto\">\n" + html + "</div>\n</body>\n</html>\n";
    }

    public String text() {
        return text.toString().strip() + "\n";
    }

    /** HTML already escaped by the subclass. */
    protected void appendHtml(String content) {
        html.append(content);
    }

    protected void appendText(String content) {
        text.append(content);
    }

    protected static String escape(String content) {
        return HtmlUtils.htmlEscape(content, "UTF-8");
    }
}
