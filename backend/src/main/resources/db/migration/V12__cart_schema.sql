-- Cart module (T-E2-01, UC-08, UC-09). The cart does not reserve stock: the reservation starts with the payment (E3).
-- Customer data, so Row Level Security is enabled and forced on both tables (RNF-27). variant_id, user_id and
-- coupon_id refer to other modules and have no foreign key (ARCHITECTURE.md section 3, rule 6).

CREATE TABLE cart (
    id         UUID                     NOT NULL,
    -- A guest's cart: SHA-256 in lowercase hex of the cart_token cookie (AccessToken.hash()). The token itself is
    -- never stored (RNF-29.6).
    token_hash TEXT,
    -- A customer's cart (E4); refers to the accounts module.
    user_id    UUID,
    -- UC-10: the applied coupon, at most one; refers to the promotions module.
    coupon_id  UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Last activity: the daily cleanup deletes carts idle for more than 30 days (T-E2-04).
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT cart_pk PRIMARY KEY (id),
    -- One cart per browser and one per customer.
    CONSTRAINT cart_token_hash_uk UNIQUE (token_hash),
    CONSTRAINT cart_user_uk UNIQUE (user_id),
    CONSTRAINT cart_owner_ck CHECK (num_nonnulls(token_hash, user_id) = 1),
    CONSTRAINT cart_token_hash_format_ck CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX cart_updated_at_idx ON cart (updated_at);

CREATE TABLE cart_item (
    id                    UUID                     NOT NULL,
    cart_id               UUID                     NOT NULL,
    -- Refers to the catalog module.
    variant_id            UUID                     NOT NULL,
    -- UC-08: never above the available stock (checked by the Cart aggregate); 1,000,000 is the stock maximum (UC-07).
    quantity              INTEGER                  NOT NULL,
    -- UC-09: the variant price the customer last saw, integer cents ARS (R-01), so a price change is announced.
    last_seen_price_cents BIGINT                   NOT NULL,
    -- Items show in the order they were added.
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT cart_item_pk PRIMARY KEY (id),
    CONSTRAINT cart_item_cart_fk FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE,
    -- UC-08: adding a variant already in the cart adds to its line.
    CONSTRAINT cart_item_cart_variant_uk UNIQUE (cart_id, variant_id),
    CONSTRAINT cart_item_quantity_ck CHECK (quantity BETWEEN 1 AND 1000000),
    CONSTRAINT cart_item_last_seen_price_ck CHECK (last_seen_price_cents > 0)
);

ALTER TABLE cart ENABLE ROW LEVEL SECURITY;
ALTER TABLE cart FORCE ROW LEVEL SECURITY;
ALTER TABLE cart_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE cart_item FORCE ROW LEVEL SECURITY;

-- RNF-27: a customer does everything on its own cart, never on a guest's.
CREATE POLICY cart_customer ON cart FOR ALL TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id())
    WITH CHECK (app_role() = 'CUSTOMER' AND user_id = app_user_id());

-- RNF-27: a guest does everything on the cart of its token, never on a customer's.
CREATE POLICY cart_guest ON cart FOR ALL TO tienda_app
    USING (app_role() = 'ANONYMOUS' AND token_hash = app_cart_token_hash())
    WITH CHECK (app_role() = 'ANONYMOUS' AND token_hash = app_cart_token_hash());

-- RNF-27: the system only finds and deletes idle carts (T-E2-04). Their items go with them through the foreign key.
CREATE POLICY cart_system_select ON cart FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY cart_system_delete ON cart FOR DELETE TO tienda_app
    USING (app_role() = 'SYSTEM');

-- RNF-27: items follow their cart. The subquery reads cart under its own policies, so it only finds the actor's cart.
CREATE POLICY cart_item_owner ON cart_item FOR ALL TO tienda_app
    USING (app_role() IN ('CUSTOMER', 'ANONYMOUS') AND EXISTS (SELECT 1 FROM cart c WHERE c.id = cart_id))
    WITH CHECK (app_role() IN ('CUSTOMER', 'ANONYMOUS') AND EXISTS (SELECT 1 FROM cart c WHERE c.id = cart_id));
