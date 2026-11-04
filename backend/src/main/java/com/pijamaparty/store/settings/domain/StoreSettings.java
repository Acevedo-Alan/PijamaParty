package com.pijamaparty.store.settings.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * UC-36: the store's settings, a single one for the whole store. Each optional field is empty when the store has
 * none, and the store then leaves it out. {@link #version()} grows by one with each save, so two saves made from the
 * same version cannot both win.
 */
public record StoreSettings(Optional<ContactEmail> email, Optional<WhatsAppNumber> whatsAppNumber,
                            Optional<PlainText> openingHours, Optional<PlainText> address,
                            Optional<SocialLink> instagram, Optional<SocialLink> facebook,
                            Optional<SocialLink> tiktok, Optional<PlainText> announcement,
                            LowStockThreshold lowStockThreshold, long version) {

    public static final int MAX_ANNOUNCEMENT = 80;
    public static final int MAX_OPENING_HOURS = 80;
    public static final int MAX_ADDRESS = 120;

    /** The fields of the form, named in a refused save. */
    public enum Field {
        EMAIL,
        WHATSAPP_NUMBER,
        OPENING_HOURS,
        ADDRESS,
        INSTAGRAM_URL,
        FACEBOOK_URL,
        TIKTOK_URL,
        ANNOUNCEMENT,
        LOW_STOCK_THRESHOLD
    }

    public StoreSettings {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(whatsAppNumber, "whatsAppNumber");
        Objects.requireNonNull(openingHours, "openingHours");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(instagram, "instagram");
        Objects.requireNonNull(facebook, "facebook");
        Objects.requireNonNull(tiktok, "tiktok");
        Objects.requireNonNull(announcement, "announcement");
        Objects.requireNonNull(lowStockThreshold, "lowStockThreshold");
        requireNetwork(instagram, SocialLink.Network.INSTAGRAM);
        requireNetwork(facebook, SocialLink.Network.FACEBOOK);
        requireNetwork(tiktok, SocialLink.Network.TIKTOK);
        if (version < 0) {
            throw new IllegalArgumentException("negative version");
        }
    }

    /**
     * The values as the administrator typed them: null or blank is "none"; the threshold is required.
     *
     * @param lowStockThreshold null when missing, which is refused
     */
    public record Typed(String email, String whatsAppNumber, String openingHours, String address,
                        String instagramUrl, String facebookUrl, String tiktokUrl, String announcement,
                        Long lowStockThreshold) {
    }

    /** What {@link #edit(Typed)} gives. */
    public sealed interface Edit {

        /** The new settings, one version after these. */
        record Edited(StoreSettings settings) implements Edit {}

        /** Nothing changes; these fields break their rules. */
        record Invalid(Set<Field> fields) implements Edit {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }
    }

    /** UC-36: these settings replaced by every typed value, or the fields that break their rules. */
    public Edit edit(Typed typed) {
        Objects.requireNonNull(typed, "typed");
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        var newEmail = optional(typed.email(), ContactEmail::parse, Field.EMAIL, invalid);
        var newWhatsApp = optional(typed.whatsAppNumber(), WhatsAppNumber::parse, Field.WHATSAPP_NUMBER, invalid);
        var newHours = optional(typed.openingHours(), text -> PlainText.parse(text, MAX_OPENING_HOURS),
            Field.OPENING_HOURS, invalid);
        var newAddress = optional(typed.address(), text -> PlainText.parse(text, MAX_ADDRESS), Field.ADDRESS,
            invalid);
        var newInstagram = optional(typed.instagramUrl(),
            url -> SocialLink.parse(SocialLink.Network.INSTAGRAM, url), Field.INSTAGRAM_URL, invalid);
        var newFacebook = optional(typed.facebookUrl(),
            url -> SocialLink.parse(SocialLink.Network.FACEBOOK, url), Field.FACEBOOK_URL, invalid);
        var newTiktok = optional(typed.tiktokUrl(),
            url -> SocialLink.parse(SocialLink.Network.TIKTOK, url), Field.TIKTOK_URL, invalid);
        var newAnnouncement = optional(typed.announcement(), text -> PlainText.parse(text, MAX_ANNOUNCEMENT),
            Field.ANNOUNCEMENT, invalid);
        Optional<LowStockThreshold> newThreshold = Optional.ofNullable(typed.lowStockThreshold())
            .flatMap(LowStockThreshold::parse);
        if (newThreshold.isEmpty()) {
            invalid.add(Field.LOW_STOCK_THRESHOLD);
        }
        if (!invalid.isEmpty()) {
            return new Edit.Invalid(invalid);
        }
        return new Edit.Edited(new StoreSettings(newEmail, newWhatsApp, newHours, newAddress, newInstagram,
            newFacebook, newTiktok, newAnnouncement, newThreshold.orElseThrow(), version + 1));
    }

    /** Whether {@code other} holds the same values, whatever its version. */
    public boolean sameValuesAs(StoreSettings other) {
        return withVersion(0).equals(other.withVersion(0));
    }

    private StoreSettings withVersion(long newVersion) {
        return new StoreSettings(email, whatsAppNumber, openingHours, address, instagram, facebook, tiktok,
            announcement, lowStockThreshold, newVersion);
    }

    /** A blank value is "none"; any other is parsed, and a refused one adds its field to {@code invalid}. */
    private static <T> Optional<T> optional(String typed, Function<String, Optional<T>> parse, Field field,
                                            Set<Field> invalid) {
        if (typed == null || typed.isBlank()) {
            return Optional.empty();
        }
        Optional<T> parsed = parse.apply(typed);
        if (parsed.isEmpty()) {
            invalid.add(field);
        }
        return parsed;
    }

    private static void requireNetwork(Optional<SocialLink> link, SocialLink.Network network) {
        if (link.filter(candidate -> candidate.network() != network).isPresent()) {
            throw new IllegalArgumentException("not a link to " + network);
        }
    }
}
