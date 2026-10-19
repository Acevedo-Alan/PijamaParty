package com.pijamaparty.store.catalog.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The character banner of the home page (UC-32). There is one; it starts hidden, without texts and without a photo.
 * The texts are edited together, the photo is replaced, and it is published only with both: a published banner is
 * always complete.
 */
public final class HomeBanner {

    private Texts texts;
    private HomeBannerPhoto photo;
    private boolean published;
    private Instant updatedAt;

    private HomeBanner(Texts texts, HomeBannerPhoto photo, boolean published, Instant updatedAt) {
        this.texts = texts;
        this.photo = photo;
        this.published = published;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (published && !missing().isEmpty()) {
            throw new IllegalArgumentException("a published banner has its texts and its photo");
        }
    }

    /** Rebuilds the stored banner; only persistence adapters call it. */
    public static HomeBanner restore(Optional<Texts> texts, Optional<HomeBannerPhoto> photo, boolean published,
                                     Instant updatedAt) {
        return new HomeBanner(texts.orElse(null), photo.orElse(null), published, updatedAt);
    }

    /** UC-32: replaces the texts with what the administrator typed, trimmed; nothing changes if one breaks a rule. */
    public EditOutcome edit(String title, String body, String linkText, String linkPath, Instant now) {
        Optional<String> parsedTitle = singleLine(title, Texts.MAX_TITLE);
        Optional<String> parsedBody = Optional.ofNullable(body).map(String::strip)
            .filter(text -> PlainText.isMultiLine(text, Texts.MAX_BODY));
        Optional<String> parsedLinkText = singleLine(linkText, Texts.MAX_LINK_TEXT);
        Optional<StorePath> parsedLinkPath = StorePath.parse(linkPath);
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        if (parsedTitle.isEmpty()) {
            invalid.add(Field.TITLE);
        }
        if (parsedBody.isEmpty()) {
            invalid.add(Field.BODY);
        }
        if (parsedLinkText.isEmpty()) {
            invalid.add(Field.LINK_TEXT);
        }
        if (parsedLinkPath.isEmpty()) {
            invalid.add(Field.LINK_PATH);
        }
        if (!invalid.isEmpty()) {
            return new EditOutcome.Invalid(invalid);
        }
        texts = new Texts(parsedTitle.get(), parsedBody.get(), parsedLinkText.get(), parsedLinkPath.get());
        updatedAt = now;
        return new EditOutcome.Edited();
    }

    /** UC-32: shows the banner on the home page, unless something it needs is missing; publishing again is a no-op. */
    public PublishOutcome publish(Instant now) {
        Set<Part> missing = missing();
        if (!missing.isEmpty()) {
            return new PublishOutcome.Incomplete(missing);
        }
        if (!published) {
            published = true;
            updatedAt = now;
        }
        return new PublishOutcome.Published();
    }

    /** UC-32: takes the banner off the home page; hiding needs nothing. */
    public void hide(Instant now) {
        if (published) {
            published = false;
            updatedAt = now;
        }
    }

    /**
     * UC-32 with the upload rule of UC-06: whether a photo with this upload key and file can replace the current one.
     * Only the current photo's key is remembered, so a retry is recognized until another photo replaces it.
     */
    public PhotoUpload checkPhoto(UploadKey uploadKey, ContentHash contentHash) {
        if (photo != null && photo.uploadKey().equals(uploadKey)) {
            return photo.contentHash().equals(contentHash)
                ? new PhotoUpload.AlreadyUploaded(photo)
                : new PhotoUpload.KeyReused();
        }
        return new PhotoUpload.Ready();
    }

    /** Replaces the photo, unless {@link #checkPhoto} refuses it; never answers {@code Ready}. */
    public PhotoUpload replacePhoto(HomeBannerPhoto newPhoto, Instant now) {
        PhotoUpload check = checkPhoto(newPhoto.uploadKey(), newPhoto.contentHash());
        if (!(check instanceof PhotoUpload.Ready)) {
            return check;
        }
        Optional<HomeBannerPhoto> previous = photo();
        photo = newPhoto;
        updatedAt = now;
        return new PhotoUpload.Replaced(newPhoto, previous);
    }

    /** What publishing needs and the banner lacks, in the order of {@link Part}. */
    public Set<Part> missing() {
        Set<Part> missing = EnumSet.noneOf(Part.class);
        if (photo == null) {
            missing.add(Part.PHOTO);
        }
        if (texts == null) {
            missing.addAll(EnumSet.of(Part.TITLE, Part.LINK_TEXT, Part.LINK_PATH));
        }
        return missing;
    }

    public HomeBanner copy() {
        return new HomeBanner(texts, photo, published, updatedAt);
    }

    /** Empty until the administrator first saves them. */
    public Optional<Texts> texts() {
        return Optional.ofNullable(texts);
    }

    public Optional<HomeBannerPhoto> photo() {
        return Optional.ofNullable(photo);
    }

    public boolean published() {
        return published;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static Optional<String> singleLine(String raw, int maxLength) {
        return Optional.ofNullable(raw).map(String::strip).filter(text -> PlainText.isSingleLine(text, maxLength));
    }

    /** Title 1 to 60 characters, text 0 to 200 (line breaks allowed), link text 1 to 30; all trimmed. */
    public record Texts(String title, String body, String linkText, StorePath linkPath) {

        public static final int MAX_TITLE = 60;
        public static final int MAX_BODY = 200;
        public static final int MAX_LINK_TEXT = 30;

        public Texts {
            Objects.requireNonNull(linkPath, "linkPath");
            if (title == null || !PlainText.isSingleLine(title, MAX_TITLE)
                || body == null || !PlainText.isMultiLine(body, MAX_BODY)
                || linkText == null || !PlainText.isSingleLine(linkText, MAX_LINK_TEXT)) {
                throw new IllegalArgumentException("not valid banner texts");
            }
        }
    }

    /** The texts the administrator edits. */
    public enum Field {
        TITLE,
        BODY,
        LINK_TEXT,
        LINK_PATH
    }

    /** What publishing needs. */
    public enum Part {
        PHOTO,
        TITLE,
        LINK_TEXT,
        LINK_PATH
    }

    public sealed interface EditOutcome {

        record Edited() implements EditOutcome {}

        record Invalid(Set<Field> fields) implements EditOutcome {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }
    }

    public sealed interface PublishOutcome {

        record Published() implements PublishOutcome {}

        record Incomplete(Set<Part> missing) implements PublishOutcome {

            public Incomplete {
                missing = Set.copyOf(missing);
            }
        }
    }

    public sealed interface PhotoUpload {

        /** {@link #checkPhoto} only: the photo can replace the current one. */
        record Ready() implements PhotoUpload {}

        /** {@link #replacePhoto} only: the photo replaced {@code previous}, whose files can go. */
        record Replaced(HomeBannerPhoto photo, Optional<HomeBannerPhoto> previous) implements PhotoUpload {}

        /** A retry: the current photo is the one of this upload key and file. */
        record AlreadyUploaded(HomeBannerPhoto photo) implements PhotoUpload {}

        /** The upload key belongs to the current photo, of another file. */
        record KeyReused() implements PhotoUpload {}
    }
}
