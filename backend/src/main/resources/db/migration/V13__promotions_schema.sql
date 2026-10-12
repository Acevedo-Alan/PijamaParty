-- Promotions module (T-E2-01, UC-10, UC-11): code coupons and their uses. No Row Level Security: no personal data, and
-- the tables are outside the RNF-27 scope list. E5 adds automatic coupons (UC-23).

CREATE TABLE coupon (
    id                     UUID                     NOT NULL,
    -- UC-11: unique ignoring case, so it is stored in upper case: 3 to 20 letters, digits or hyphens.
    code                   TEXT                     NOT NULL,
    type                   TEXT                     NOT NULL,
    -- PERCENTAGE: a whole percent from 1 to 100. FIXED_AMOUNT: integer cents ARS (R-01) above 0.
    discount_value         BIGINT                   NOT NULL,
    -- UC-10: both optional and inclusive, as dates in America/Argentina/Buenos_Aires (R-07).
    valid_from             DATE,
    valid_until            DATE,
    -- Null: unlimited uses.
    max_uses               INTEGER,
    -- Null: no minimum. Integer cents ARS.
    minimum_purchase_cents BIGINT,
    active                 BOOLEAN                  NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT coupon_pk PRIMARY KEY (id),
    CONSTRAINT coupon_code_uk UNIQUE (code),
    CONSTRAINT coupon_code_format_ck CHECK (code ~ '^[A-Z0-9-]{3,20}$'),
    CONSTRAINT coupon_type_ck CHECK (type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT coupon_discount_value_ck CHECK (
        discount_value > 0 AND (type <> 'PERCENTAGE' OR discount_value <= 100)),
    CONSTRAINT coupon_validity_ck CHECK (valid_from <= valid_until),
    CONSTRAINT coupon_max_uses_ck CHECK (max_uses > 0),
    CONSTRAINT coupon_minimum_purchase_ck CHECK (minimum_purchase_cents > 0)
);

-- UC-10, UC-11: one row per confirmed order that used the coupon (filled in E3). The number of uses is counted from
-- here; the primary key makes a repeated confirmation count once (RNF-20), and the foreign key refuses deleting a used
-- coupon. order_id refers to the ordering module and has no foreign key (ARCHITECTURE.md section 3, rule 6).
CREATE TABLE coupon_redemption (
    coupon_id   UUID                     NOT NULL,
    order_id    UUID                     NOT NULL,
    redeemed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT coupon_redemption_pk PRIMARY KEY (coupon_id, order_id),
    CONSTRAINT coupon_redemption_coupon_fk FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE RESTRICT
);
