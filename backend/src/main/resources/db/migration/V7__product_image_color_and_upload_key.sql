-- T-E1-05, UC-06: a photo shows a color in every size, so it belongs to one of the product's colors (or to the whole
-- product) instead of a variant. The color is checked against the product's variants by the Product aggregate, under
-- the product row lock.
ALTER TABLE product_image DROP CONSTRAINT product_image_variant_fk;
ALTER TABLE product_image DROP COLUMN variant_id;
-- It only existed as the target of product_image_variant_fk.
ALTER TABLE variant DROP CONSTRAINT variant_id_product_uk;

-- Uploads are idempotent (UC-06, R-09): the Idempotency-Key of an upload is unique per product, and the SHA-256 of the
-- uploaded file tells a retry (same file) from a reused key (another file). No photo has been uploaded before this
-- migration, so the new columns need no default.
ALTER TABLE product_image
    ADD COLUMN color          TEXT,
    ADD COLUMN upload_key     UUID  NOT NULL,
    ADD COLUMN content_sha256 BYTEA NOT NULL,
    ADD CONSTRAINT product_image_color_length_ck CHECK (color = btrim(color) AND char_length(color) BETWEEN 1 AND 30),
    ADD CONSTRAINT product_image_content_sha256_ck CHECK (octet_length(content_sha256) = 32),
    ADD CONSTRAINT product_image_upload_key_uk UNIQUE (product_id, upload_key);
