-- T-E8-04 (UC-36): the store's contact data, social links, header announcement and low-stock threshold, edited in
-- "Datos de la tienda". A single row, created here, that the application only updates. No Row Level Security: the
-- store's own business data, no personal data of buyers or customers (RowLevelSecurityCoverageTest lists it public).
-- The checks back the domain rules, so a value the domain let through by mistake fails instead of being stored.

CREATE TABLE store_settings (
    id                  BOOLEAN                  NOT NULL DEFAULT TRUE,
    -- Null: none. Lower case, as the buyer email of UC-13.
    email               TEXT,
    -- Digits only, in the international format wa.me takes (549 + area code + number).
    whatsapp_number     TEXT,
    -- Plain texts as they should read in the footer (T-E7-26).
    opening_hours       TEXT,
    address             TEXT,
    -- https links to the network itself, with a path; the domain also refuses user info and ports.
    instagram_url       TEXT,
    facebook_url        TEXT,
    tiktok_url          TEXT,
    -- The header strip's text; null: the free-shipping message (UC-31).
    announcement        TEXT,
    -- D-E8-03: a variant with this many available units or fewer is low on stock.
    low_stock_threshold INTEGER                  NOT NULL,
    -- The optimistic check of two saves from the same version (UC-36).
    version             BIGINT                   NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT store_settings_pk PRIMARY KEY (id),
    CONSTRAINT store_settings_single_row_ck CHECK (id),
    CONSTRAINT store_settings_email_ck CHECK (
        char_length(email) <= 254 AND email = lower(email) AND email ~ '^[!-?A-~]+@[!-?A-~]+$'),
    CONSTRAINT store_settings_whatsapp_number_ck CHECK (whatsapp_number ~ '^[0-9]{10,15}$'),
    CONSTRAINT store_settings_opening_hours_ck CHECK (
        char_length(opening_hours) BETWEEN 1 AND 80 AND opening_hours !~ '[[:cntrl:]]'
        AND opening_hours = btrim(opening_hours)),
    CONSTRAINT store_settings_address_ck CHECK (
        char_length(address) BETWEEN 1 AND 120 AND address !~ '[[:cntrl:]]' AND address = btrim(address)),
    CONSTRAINT store_settings_instagram_url_ck CHECK (
        char_length(instagram_url) <= 200 AND instagram_url ~* '^https://(www\.)?instagram\.com/[^[:space:]]'),
    CONSTRAINT store_settings_facebook_url_ck CHECK (
        char_length(facebook_url) <= 200 AND facebook_url ~* '^https://(www\.|m\.)?facebook\.com/[^[:space:]]'),
    CONSTRAINT store_settings_tiktok_url_ck CHECK (
        char_length(tiktok_url) <= 200 AND tiktok_url ~* '^https://(www\.)?tiktok\.com/[^[:space:]]'),
    CONSTRAINT store_settings_announcement_ck CHECK (
        char_length(announcement) BETWEEN 1 AND 80 AND announcement !~ '[[:cntrl:]]'
        AND announcement = btrim(announcement)),
    CONSTRAINT store_settings_low_stock_threshold_ck CHECK (low_stock_threshold BETWEEN 0 AND 1000),
    CONSTRAINT store_settings_version_ck CHECK (version >= 0)
);

-- UC-36 (decided on 2026-11-04): the WhatsApp number and the Instagram link the business already sent (D-15 of E7)
-- stay on the store; everything else starts empty, and the threshold at the default of D-E8-03.
INSERT INTO store_settings (id, whatsapp_number, instagram_url, low_stock_threshold, version, updated_at)
VALUES (TRUE, '5493425304507', 'https://www.instagram.com/pijamaparty.zzz/', 3, 0, now());

REVOKE INSERT, DELETE, TRUNCATE ON TABLE store_settings FROM tienda_app;
