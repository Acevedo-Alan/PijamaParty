package com.pijamaparty.store.settings.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-36: the rules of each value of "Datos de la tienda". */
class StoreSettingsTest {

    private static final StoreSettings EMPTY = new StoreSettings(Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        LowStockThreshold.DEFAULT, 4);

    private static StoreSettings.Typed typed(String email, String whatsApp, String instagram, String announcement,
                                             Long threshold) {
        return new StoreSettings.Typed(email, whatsApp, null, null, instagram, null, null, announcement, threshold);
    }

    @Test
    void givenAWhatsAppNumberWithSpacesPlusAndDashes_whenParsed_thenOnlyItsDigitsRemain() {
        assertThat(WhatsAppNumber.parse("+54 9 342 400-0000")).map(WhatsAppNumber::digits).contains("5493424000000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"342400000", "5493424000000123", "54 9 342 abc", "+", "(342) 4000000"})
    void givenAWhatsAppNumberThatIsNot10To15Digits_whenParsed_thenItIsRefused(String typed) {
        assertThat(WhatsAppNumber.parse(typed)).isEmpty();
    }

    @Test
    void givenAnEmailWithCapitalsAndSpaces_whenParsed_thenItIsTrimmedInLowerCase() {
        assertThat(ContactEmail.parse("  Hola@PijamaParty.com.ar ")).map(ContactEmail::value)
            .contains("hola@pijamaparty.com.ar");
    }

    @Test
    void givenAnEmailThatBreaksTheRuleOfUc13_whenParsed_thenItIsRefused() {
        assertThat(ContactEmail.parse("sin-arroba")).isEmpty();
        assertThat(ContactEmail.parse("con espacio@x.com")).isEmpty();
        assertThat(ContactEmail.parse("a@" + "b".repeat(253))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://instagram.com/pijamaparty", "https://www.instagram.com/pijamaparty.zzz/",
        "https://WWW.Instagram.com/pijamaparty?hl=es"})
    void givenALinkToInstagramItself_whenParsed_thenItIsAccepted(String url) {
        assertThat(SocialLink.parse(SocialLink.Network.INSTAGRAM, url)).map(SocialLink::url).contains(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/instagram.com", "http://instagram.com/x",
        "https://instagram.com.evil.example/x", "https://instagram.com", "https://instagram.com/",
        "https://user@instagram.com/x", "https://instagram.com:443/x", "https:/instagram.com/x",
        "https:///instagram.com/x", "javascript:alert(1)//instagram.com/x", "https://facebook.com/x",
        "https://instagram.com/a b"})
    void givenALinkThatCouldLeadElsewhere_whenParsed_thenItIsRefused(String url) {
        assertThat(SocialLink.parse(SocialLink.Network.INSTAGRAM, url)).isEmpty();
    }

    @Test
    void givenALinkOver200Characters_whenParsed_thenItIsRefused() {
        String url = "https://www.facebook.com/" + "a".repeat(200 - 25);
        assertThat(SocialLink.parse(SocialLink.Network.FACEBOOK, url)).isPresent();
        assertThat(SocialLink.parse(SocialLink.Network.FACEBOOK, url + "a")).isEmpty();
    }

    @Test
    void givenEachNetwork_whenItsHostsAreTried_thenOnlyItsOwnAreAccepted() {
        assertThat(SocialLink.parse(SocialLink.Network.FACEBOOK, "https://m.facebook.com/pijamaparty")).isPresent();
        assertThat(SocialLink.parse(SocialLink.Network.TIKTOK, "https://www.tiktok.com/@pijamaparty")).isPresent();
        assertThat(SocialLink.parse(SocialLink.Network.TIKTOK, "https://m.tiktok.com/@pijamaparty")).isEmpty();
    }

    @Test
    void givenATextOnOneLine_whenParsed_thenItIsTrimmedAndCountedInCharacters() {
        assertThat(PlainText.parse("  Envío gratis a todo Santa Fe  ", 80)).map(PlainText::value)
            .contains("Envío gratis a todo Santa Fe");
        assertThat(PlainText.parse("ñ".repeat(80), 80)).isPresent();
        assertThat(PlainText.parse("ñ".repeat(81), 80)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Envío gratis\na todo el país", "Envío\tgratis", "Envío gratis", "Envío\u0000"})
    void givenATextWithALineBreakOrAControlCharacter_whenParsed_thenItIsRefused(String text) {
        assertThat(PlainText.parse(text, 80)).isEmpty();
    }

    @Test
    void givenEveryValueValid_whenEdited_thenTheSettingsHaveThemOneVersionLater() {
        var edit = EMPTY.edit(new StoreSettings.Typed(" HOLA@pijamaparty.com.ar", "+54 9 342 400-0000",
            "Lunes a viernes de 9 a 18 h", "San Martín 2000, Santa Fe", "https://instagram.com/pijamaparty",
            "https://facebook.com/pijamaparty", "https://tiktok.com/@pijamaparty", "Envío gratis a todo Santa Fe",
            0L));

        assertThat(edit).isInstanceOf(StoreSettings.Edit.Edited.class);
        StoreSettings settings = ((StoreSettings.Edit.Edited) edit).settings();
        assertThat(settings.email()).map(ContactEmail::value).contains("hola@pijamaparty.com.ar");
        assertThat(settings.whatsAppNumber()).map(WhatsAppNumber::digits).contains("5493424000000");
        assertThat(settings.openingHours()).map(PlainText::value).contains("Lunes a viernes de 9 a 18 h");
        assertThat(settings.address()).map(PlainText::value).contains("San Martín 2000, Santa Fe");
        assertThat(settings.facebook()).map(SocialLink::network).contains(SocialLink.Network.FACEBOOK);
        assertThat(settings.lowStockThreshold().units()).isZero();
        assertThat(settings.version()).isEqualTo(5);
    }

    @Test
    void givenBlankOrNullValues_whenEdited_thenEachIsNone() {
        var edit = EMPTY.edit(typed("  ", "", null, " ", 3L));

        assertThat(edit).isEqualTo(new StoreSettings.Edit.Edited(new StoreSettings(Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), LowStockThreshold.DEFAULT, 5)));
    }

    @Test
    void givenSeveralInvalidValues_whenEdited_thenEachIsNamed() {
        var edit = EMPTY.edit(typed("sin-arroba", "123", "http://instagram.com/x", "a".repeat(81), 1001L));

        assertThat(edit).isEqualTo(new StoreSettings.Edit.Invalid(java.util.Set.of(StoreSettings.Field.EMAIL,
            StoreSettings.Field.WHATSAPP_NUMBER, StoreSettings.Field.INSTAGRAM_URL, StoreSettings.Field.ANNOUNCEMENT,
            StoreSettings.Field.LOW_STOCK_THRESHOLD)));
    }

    @Test
    void givenAThresholdOutOfRangeOrMissing_whenEdited_thenItIsRefused() {
        assertThat(EMPTY.edit(typed(null, null, null, null, -1L))).isInstanceOf(StoreSettings.Edit.Invalid.class);
        assertThat(EMPTY.edit(typed(null, null, null, null, 1001L))).isInstanceOf(StoreSettings.Edit.Invalid.class);
        assertThat(EMPTY.edit(typed(null, null, null, null, null))).isInstanceOf(StoreSettings.Edit.Invalid.class);
        assertThat(EMPTY.edit(typed(null, null, null, null, 1000L))).isInstanceOf(StoreSettings.Edit.Edited.class);
    }

    @Test
    void givenHoursOver80OrAnAddressOver120Characters_whenEdited_thenTheyAreRefused() {
        var edit = EMPTY.edit(new StoreSettings.Typed(null, null, "a".repeat(81), "a".repeat(121), null, null, null,
            null, 3L));

        assertThat(edit).isEqualTo(new StoreSettings.Edit.Invalid(java.util.Set.of(StoreSettings.Field.OPENING_HOURS,
            StoreSettings.Field.ADDRESS)));
    }

    @Test
    void givenTheSameValuesInAnotherVersion_whenCompared_thenTheyAreTheSame() {
        StoreSettings next = ((StoreSettings.Edit.Edited) EMPTY.edit(typed(null, null, null, null, 3L))).settings();

        assertThat(next.sameValuesAs(EMPTY)).isTrue();
        assertThat(next).isNotEqualTo(EMPTY);
    }
}
