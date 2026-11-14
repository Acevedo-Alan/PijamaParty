package com.pijamaparty.store.notifications.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.settings.api.StoreContact.Contact;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** T-E3-33: the footer of every email, with the store's contact as set in "Datos de la tienda" (UC-36). */
class EmailWriterTest {

    private static final URI SHOP = URI.create("https://pijamaparty.example/");
    private static final Contact ALL = new Contact(Optional.of("hola@pijamaparty.example"),
        Optional.of("5493424000000"), Optional.of("https://www.instagram.com/pijamaparty"),
        Optional.of("https://www.facebook.com/pijamaparty"), Optional.of("https://www.tiktok.com/@pijamaparty"));

    /** The parts of every email, and nothing else. */
    private static final class Writer extends EmailWriter<Writer> {

        Writer(Contact contact) {
            super(messages(), SHOP, contact);
        }

        @Override
        protected Writer self() {
            return this;
        }
    }

    private static ResourceBundleMessageSource messages() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    private static Writer written(Contact contact) {
        return new Writer(contact).paragraph("Hola");
    }

    @Test
    void givenEveryContactSet_whenWritten_thenTheFooterHasTheContactRowAndTheNetworksRow() {
        String html = written(ALL).html();

        assertThat(html).contains("<a href=\"https://wa.me/5493424000000\" style=\"color:#a83f57\">"
            + "WhatsApp +54 9 3424000000</a> · <a href=\"mailto:hola@pijamaparty.example\" style=\"color:#a83f57\">"
            + "hola@pijamaparty.example</a>");
        assertThat(html).contains("<a href=\"https://www.instagram.com/pijamaparty\" style=\"color:#a83f57\">"
            + "Instagram</a> · <a href=\"https://www.facebook.com/pijamaparty\" style=\"color:#a83f57\">Facebook</a>"
            + " · <a href=\"https://www.tiktok.com/@pijamaparty\" style=\"color:#a83f57\">TikTok</a>");
        assertThat(html.indexOf("Pijama Party · Santa Fe")).isLessThan(html.indexOf("wa.me"));
        assertThat(html.indexOf("TikTok")).isLessThan(html.indexOf(">pijamaparty.example</a>"));
    }

    @Test
    void givenEveryContactSet_whenWritten_thenThePlainTextListsThemOnePerLine() {
        assertThat(written(ALL).text()).isEqualTo("""
            Hola

            --
            Pijama Party · Santa Fe
            WhatsApp: +54 9 3424000000 (https://wa.me/5493424000000)
            Email: hola@pijamaparty.example
            Instagram: https://www.instagram.com/pijamaparty
            Facebook: https://www.facebook.com/pijamaparty
            TikTok: https://www.tiktok.com/@pijamaparty
            https://pijamaparty.example
            """);
    }

    @Test
    void givenNoContact_whenWritten_thenTheFooterIsTheNameAndTheStoreLinkAsBefore() {
        Writer writer = written(Contact.NONE);

        assertThat(writer.html()).contains("Pijama Party · Santa Fe<br><a href=\"https://pijamaparty.example\"")
            .doesNotContain("wa.me", "mailto:", "Instagram");
        assertThat(writer.text()).isEqualTo("Hola\n\n--\nPijama Party · Santa Fe\nhttps://pijamaparty.example\n");
    }

    @Test
    void givenOnlyTheEmail_whenWritten_thenThereIsTheContactRowAndNoNetworksRow() {
        String html = written(new Contact(Optional.of("hola@pijamaparty.example"), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty())).html();

        assertThat(html).contains("Pijama Party · Santa Fe<br><a href=\"mailto:hola@pijamaparty.example\"",
            "hola@pijamaparty.example</a><br><a href=\"https://pijamaparty.example\"");
    }

    @Test
    void givenAValueWithMarkup_whenWritten_thenTheHtmlEscapesIt() {
        String html = written(new Contact(Optional.of("a\"b@x.example"), Optional.empty(),
            Optional.of("https://www.instagram.com/<b>x"), Optional.empty(), Optional.empty())).html();

        assertThat(html).contains("mailto:a&quot;b@x.example", "https://www.instagram.com/&lt;b&gt;x")
            .doesNotContain("<b>x");
    }

    @Test
    void givenANumberOfThirteenDigits_whenShown_thenItReadsAsTheStorefrontShowsIt() {
        assertThat(EmailWriter.displayWhatsApp("5493424000000")).isEqualTo("+54 9 3424000000");
    }
}
