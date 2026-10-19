-- T-E7-13: the character banner of the home page (UC-32, docs/use-cases/E7-storefront-design.md). Catalog content
-- without personal data: no Row Level Security (RNF-27). The domain (HomeBanner) holds the rules; these checks back them.

-- There is one banner, created here hidden and empty. Its texts are saved together, and so are the columns of its
-- photo (the WebP renditions of UC-06 under /uploads, and the upload key and SHA-256 that make the upload idempotent).
CREATE TABLE home_banner (
    id                   SMALLINT                 NOT NULL,
    title                TEXT,
    body                 TEXT,
    link_text            TEXT,
    link_path            TEXT,
    image_storage_key    TEXT,
    image_placeholder    TEXT,
    image_upload_key     UUID,
    image_content_sha256 BYTEA,
    published            BOOLEAN                  NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT home_banner_pk PRIMARY KEY (id),
    CONSTRAINT home_banner_single_ck CHECK (id = 1),
    CONSTRAINT home_banner_texts_together_ck CHECK (
        (title IS NULL) = (body IS NULL) AND (title IS NULL) = (link_text IS NULL)
        AND (title IS NULL) = (link_path IS NULL)),
    CONSTRAINT home_banner_title_ck CHECK (title = btrim(title) AND char_length(title) BETWEEN 1 AND 60),
    CONSTRAINT home_banner_body_ck CHECK (body = btrim(body) AND char_length(body) <= 200),
    CONSTRAINT home_banner_link_text_ck CHECK (
        link_text = btrim(link_text) AND char_length(link_text) BETWEEN 1 AND 30),
    -- A path of this store (StorePath): one leading slash, no backslash, no control characters.
    CONSTRAINT home_banner_link_path_ck CHECK (
        link_path = btrim(link_path) AND char_length(link_path) <= 200 AND link_path LIKE '/%'
        AND link_path NOT LIKE '//%' AND strpos(link_path, '\') = 0 AND link_path !~ '[[:cntrl:]]'),
    CONSTRAINT home_banner_image_together_ck CHECK (
        (image_storage_key IS NULL) = (image_placeholder IS NULL)
        AND (image_storage_key IS NULL) = (image_upload_key IS NULL)
        AND (image_storage_key IS NULL) = (image_content_sha256 IS NULL)),
    CONSTRAINT home_banner_image_storage_key_ck CHECK (image_storage_key ~ '^[a-z0-9][a-z0-9-]{0,99}$'),
    -- Only base64 after the prefix: nothing that could break out of an HTML attribute or a CSS url() (as V3).
    CONSTRAINT home_banner_image_placeholder_ck CHECK (
        image_placeholder ~ '^data:image/webp;base64,[A-Za-z0-9+/]+={0,2}$' AND char_length(image_placeholder) <= 4000),
    CONSTRAINT home_banner_image_content_sha256_ck CHECK (octet_length(image_content_sha256) = 32),
    -- Publishing needs the texts and the photo.
    CONSTRAINT home_banner_published_complete_ck CHECK (
        NOT published OR (title IS NOT NULL AND image_storage_key IS NOT NULL))
);

INSERT INTO home_banner (id, published, updated_at) VALUES (1, FALSE, now());
