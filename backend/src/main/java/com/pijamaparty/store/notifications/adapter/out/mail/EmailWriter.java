package com.pijamaparty.store.notifications.adapter.out.mail;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.web.util.HtmlUtils;

/**
 * Writes the body of an email twice, as HTML and as plain text, with the texts of {@code messages.properties} in
 * Spanish (Argentina). Everything given to it is escaped in the HTML. Each kind of email adds its own parts in a
 * subclass. Not thread-safe: one writer per email.
 *
 * <p>The HTML is the store's layout (T-E3-33): tables and inline styles only, because email clients drop {@code
 * <style>}, web fonts and CSS variables. The colors are the storefront's tokens ({@code styles/tokens.css}).
 *
 * @param <W> the subclass, so the calls chain through its own parts too
 */
public abstract class EmailWriter<W extends EmailWriter<W>> {

    public static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");

    /** Where the storefront serves the logo as a PNG, relative to its origin (the frontend's {@code public/email}). */
    public static final String LOGO_PATH = "/email/logo.png";

    /** The page behind the card, {@code --bg}. */
    protected static final String PAGE = "#fff8f0";
    /** The text, {@code --ink}. */
    protected static final String INK = "#4a3a3a";
    /** The secondary text, {@code --ink-soft}. */
    protected static final String INK_SOFT = "#7a6a66";
    /** Buttons, headings and the total, {@code --accent-hover}: 5.7:1 with white. */
    protected static final String ACCENT = "#a83f57";
    /** The separators, {@code --line}. */
    protected static final String LINE = "#f0dcd0";
    /** The panels, {@code --peach-soft}. */
    protected static final String PANEL = "#ffede3";

    private static final String FONT = "font-family:Arial,Helvetica,sans-serif";

    private final MessageSource messages;
    private final String publicUrl;
    private final StringBuilder html = new StringBuilder();
    private final StringBuilder text = new StringBuilder();

    /** @param publicUrl the storefront's origin ({@code store.public-url}), where the logo and the footer link point */
    protected EmailWriter(MessageSource messages, URI publicUrl) {
        this.messages = messages;
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
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
        html.append("<h1 style=\"margin:0 0 16px;font-size:22px;line-height:1.3;color:").append(ACCENT).append("\">")
            .append(escape(content)).append("</h1>\n");
        text.append(content).append("\n\n");
        return self();
    }

    public W paragraph(String content) {
        html.append("<p style=\"margin:0 0 16px\">").append(escape(content)).append("</p>\n");
        text.append(content).append("\n\n");
        return self();
    }

    /** A label and the lines that follow it, kept together in a soft panel. */
    public W block(String label, List<String> lines) {
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"margin:0 0 16px\"><tr><td style=\"background:").append(PANEL)
            .append(";border-radius:8px;padding:12px 16px\"><strong>").append(escape(label)).append("</strong><br>")
            .append(String.join("<br>", lines.stream().map(EmailWriter::escape).toList())).append("</td></tr></table>\n");
        text.append(label).append('\n').append(String.join("\n", lines)).append("\n\n");
        return self();
    }

    /** A button in the HTML; in the text, the explanation and then the bare address. */
    public W link(String explanation, String button, String url) {
        html.append("<p style=\"margin:0 0 12px\">").append(escape(explanation)).append("</p>\n")
            .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 16px\">")
            .append("<tr><td style=\"background:").append(ACCENT).append(";border-radius:8px\"><a href=\"")
            .append(escape(url)).append("\" style=\"display:inline-block;padding:12px 24px;color:#ffffff;")
            .append("font-weight:bold;text-decoration:none\">").append(escape(button)).append("</a></td></tr></table>\n");
        text.append(explanation).append('\n').append(url).append("\n\n");
        return self();
    }

    public String html() {
        String shop = escape(publicUrl);
        return "<!DOCTYPE html>\n<html lang=\"es\">\n<head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + escape(message("notifications.layout.name")) + "</title></head>\n"
            + "<body style=\"margin:0;padding:0;background:" + PAGE + "\">\n"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:"
            + PAGE + "\"><tr><td align=\"center\" style=\"padding:24px 12px\">\n"
            + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"width:100%;max-width:600px\">\n"
            + "<tr><td align=\"center\" style=\"padding:0 0 16px\"><a href=\"" + shop + "\">"
            + "<img src=\"" + shop + LOGO_PATH + "\" width=\"140\" alt=\""
            + escape(message("notifications.layout.name")) + "\" style=\"display:block;border:0;height:auto\"></a>"
            + "</td></tr>\n"
            + "<tr><td style=\"background:#ffffff;border-radius:12px;padding:28px 24px;" + FONT
            + ";font-size:15px;line-height:1.5;color:" + INK + "\">\n" + html + "</td></tr>\n"
            + "<tr><td align=\"center\" style=\"padding:16px 12px;" + FONT + ";font-size:12px;line-height:1.5;color:"
            + INK_SOFT + "\">" + escape(message("notifications.layout.footer")) + "<br><a href=\"" + shop
            + "\" style=\"color:" + ACCENT + "\">" + escape(shopLabel()) + "</a></td></tr>\n"
            + "</table>\n</td></tr></table>\n</body>\n</html>\n";
    }

    public String text() {
        return text.toString().strip() + "\n\n--\n" + message("notifications.layout.footer") + "\n" + publicUrl + "\n";
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

    /** The address without the scheme, for the footer's link text. */
    private String shopLabel() {
        return publicUrl.replaceFirst("^https?://", "");
    }
}
