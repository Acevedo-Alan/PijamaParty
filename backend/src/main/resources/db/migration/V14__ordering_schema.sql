-- Ordering module (T-E3-01, UC-13 to UC-18): orders and their items. Customer data, so Row Level Security is enabled
-- and forced on both tables (RNF-27). user_id, coupon_id and variant_id refer to other modules and have no foreign key
-- (ARCHITECTURE.md section 3, rule 6). The checkout transaction runs as the buyer, bound to the new order's tracking
-- token hash; the webhook and the reservation expiry run as SYSTEM.

-- RNF-20.6: order numbers come from a sequence and are unique; shown as #1001.
CREATE SEQUENCE order_number_seq AS BIGINT START WITH 1001;

CREATE TABLE orders (
    id                       UUID                     NOT NULL,
    number                   BIGINT                   NOT NULL,
    -- A customer's order (E4); refers to the accounts module. Set to null when the account is deleted (R-15).
    user_id                  UUID,
    -- SHA-256 in lowercase hex of the tracking token (AccessToken.hash()). The token itself is never stored
    -- (RNF-29.6).
    tracking_token_hash      TEXT                     NOT NULL,
    status                   TEXT                     NOT NULL,
    -- Buyer snapshot (R-04, UC-13). Personal data: never logged (R-05). The national ID is not encrypted per column
    -- (RNF-29.7): Row Level Security, the least-privilege role and encrypted backups protect it.
    customer_name            TEXT                     NOT NULL,
    customer_email           TEXT                     NOT NULL,
    customer_phone           TEXT                     NOT NULL,
    national_id              TEXT                     NOT NULL,
    -- Shipping address snapshot (R-04).
    street                   TEXT                     NOT NULL,
    street_number            TEXT                     NOT NULL,
    floor                    TEXT,
    apartment                TEXT,
    city                     TEXT                     NOT NULL,
    province                 TEXT                     NOT NULL,
    postal_code              TEXT                     NOT NULL,
    -- Integer cents ARS (R-01), calculated by the server (R-02).
    subtotal_cents           BIGINT                   NOT NULL,
    discount_cents           BIGINT                   NOT NULL,
    shipping_cents           BIGINT                   NOT NULL,
    total_cents              BIGINT                   NOT NULL,
    -- UC-10: the coupon applied at checkout; refers to the promotions module. The code is kept as it was.
    coupon_id                UUID,
    coupon_code              TEXT,
    payment_provider         TEXT                     NOT NULL,
    -- RNF-30.6: the version of the terms the buyer accepted.
    terms_version            TEXT                     NOT NULL,
    -- UC-16: an order still awaiting payment after this instant expires and releases its stock.
    reservation_expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    -- UC-18: the carrier's tracking number, entered when the order is shipped.
    shipment_tracking_number TEXT,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    -- UC-29: the withdrawal term counts from the delivery.
    delivered_at             TIMESTAMP WITH TIME ZONE,
    CONSTRAINT orders_pk PRIMARY KEY (id),
    CONSTRAINT orders_number_uk UNIQUE (number),
    CONSTRAINT orders_tracking_token_hash_uk UNIQUE (tracking_token_hash),
    CONSTRAINT orders_tracking_token_hash_format_ck CHECK (tracking_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT orders_status_ck CHECK (status IN (
        'AWAITING_PAYMENT', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT orders_customer_name_ck CHECK (
        customer_name = btrim(customer_name) AND char_length(customer_name) BETWEEN 1 AND 100),
    CONSTRAINT orders_customer_email_ck CHECK (
        customer_email = lower(btrim(customer_email)) AND customer_email LIKE '_%@_%'
        AND char_length(customer_email) <= 254),
    CONSTRAINT orders_customer_phone_ck CHECK (customer_phone ~ '^[0-9]{8,15}$'),
    CONSTRAINT orders_national_id_ck CHECK (national_id ~ '^[0-9]{7,8}$'),
    CONSTRAINT orders_street_ck CHECK (street = btrim(street) AND char_length(street) BETWEEN 1 AND 100),
    CONSTRAINT orders_street_number_ck CHECK (
        street_number = btrim(street_number) AND char_length(street_number) BETWEEN 1 AND 10),
    CONSTRAINT orders_floor_ck CHECK (floor = btrim(floor) AND char_length(floor) BETWEEN 1 AND 10),
    CONSTRAINT orders_apartment_ck CHECK (apartment = btrim(apartment) AND char_length(apartment) BETWEEN 1 AND 10),
    CONSTRAINT orders_city_ck CHECK (city = btrim(city) AND char_length(city) BETWEEN 1 AND 80),
    -- UC-12: the 24 jurisdictions of Argentina, as the Province enum.
    CONSTRAINT orders_province_ck CHECK (province IN (
        'BUENOS_AIRES', 'CIUDAD_DE_BUENOS_AIRES', 'CATAMARCA', 'CHACO', 'CHUBUT', 'CORDOBA', 'CORRIENTES',
        'ENTRE_RIOS', 'FORMOSA', 'JUJUY', 'LA_PAMPA', 'LA_RIOJA', 'MENDOZA', 'MISIONES', 'NEUQUEN', 'RIO_NEGRO',
        'SALTA', 'SAN_JUAN', 'SAN_LUIS', 'SANTA_CRUZ', 'SANTA_FE', 'SANTIAGO_DEL_ESTERO', 'TIERRA_DEL_FUEGO',
        'TUCUMAN')),
    -- 4 digits, or an Argentine CPA: a letter, 4 digits and 3 letters (S3000ABC).
    CONSTRAINT orders_postal_code_ck CHECK (postal_code ~ '^([0-9]{4}|[A-Z][0-9]{4}[A-Z]{3})$'),
    CONSTRAINT orders_subtotal_ck CHECK (subtotal_cents > 0),
    CONSTRAINT orders_discount_ck CHECK (discount_cents BETWEEN 0 AND subtotal_cents),
    CONSTRAINT orders_shipping_ck CHECK (shipping_cents >= 0),
    CONSTRAINT orders_total_ck CHECK (total_cents = subtotal_cents - discount_cents + shipping_cents),
    CONSTRAINT orders_coupon_ck CHECK ((coupon_id IS NULL) = (coupon_code IS NULL)),
    CONSTRAINT orders_coupon_code_format_ck CHECK (coupon_code ~ '^[A-Z0-9-]{3,20}$'),
    -- MODO arrives with E3b.
    CONSTRAINT orders_payment_provider_ck CHECK (payment_provider IN ('MERCADOPAGO', 'MODO')),
    CONSTRAINT orders_terms_version_ck CHECK (
        terms_version = btrim(terms_version) AND char_length(terms_version) BETWEEN 1 AND 40),
    CONSTRAINT orders_reservation_expires_at_ck CHECK (reservation_expires_at > created_at),
    CONSTRAINT orders_shipment_tracking_number_ck CHECK (
        shipment_tracking_number = btrim(shipment_tracking_number)
        AND char_length(shipment_tracking_number) BETWEEN 1 AND 60),
    -- UC-18: an order is shipped with its tracking number.
    CONSTRAINT orders_shipped_tracking_ck CHECK (
        status NOT IN ('SHIPPED', 'DELIVERED') OR shipment_tracking_number IS NOT NULL),
    CONSTRAINT orders_delivered_at_ck CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL))
);

-- UC-18: the back-office list, filterable by status, newest first.
CREATE INDEX orders_status_created_idx ON orders (status, created_at DESC);
-- E4: a customer's orders, newest first.
CREATE INDEX orders_user_created_idx ON orders (user_id, created_at DESC) WHERE user_id IS NOT NULL;
-- UC-16: the expiry job finds the orders awaiting payment whose reservation is over.
CREATE INDEX orders_reservation_expiry_idx ON orders (reservation_expires_at) WHERE status = 'AWAITING_PAYMENT';

CREATE TABLE order_item (
    id               UUID    NOT NULL,
    order_id         UUID    NOT NULL,
    -- Items show in the order of the cart.
    position         INTEGER NOT NULL,
    -- Refers to the catalog module.
    variant_id       UUID    NOT NULL,
    -- Snapshot of the variant at checkout (R-04): later catalog changes do not alter the order.
    sku              TEXT    NOT NULL,
    product_name     TEXT    NOT NULL,
    size             TEXT    NOT NULL,
    color            TEXT    NOT NULL,
    unit_price_cents BIGINT  NOT NULL,
    quantity         INTEGER NOT NULL,
    CONSTRAINT order_item_pk PRIMARY KEY (id),
    CONSTRAINT order_item_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT order_item_order_variant_uk UNIQUE (order_id, variant_id),
    CONSTRAINT order_item_order_position_uk UNIQUE (order_id, position),
    CONSTRAINT order_item_position_ck CHECK (position >= 0),
    -- The catalog limits of UC-06 and UC-07.
    CONSTRAINT order_item_sku_ck CHECK (char_length(sku) BETWEEN 1 AND 40),
    CONSTRAINT order_item_product_name_ck CHECK (char_length(product_name) BETWEEN 1 AND 120),
    CONSTRAINT order_item_size_ck CHECK (char_length(size) BETWEEN 1 AND 20),
    CONSTRAINT order_item_color_ck CHECK (char_length(color) BETWEEN 1 AND 30),
    CONSTRAINT order_item_unit_price_ck CHECK (unit_price_cents BETWEEN 1 AND 1000000000),
    CONSTRAINT order_item_quantity_ck CHECK (quantity BETWEEN 1 AND 1000000)
);

-- Orders are kept (R-15) and their items are an immutable snapshot (R-04). The default privileges of
-- deploy/db/init/01-roles.sh grant full DML; take back what must not happen.
REVOKE DELETE, TRUNCATE ON TABLE orders FROM tienda_app;
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE order_item FROM tienda_app;

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
ALTER TABLE order_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_item FORCE ROW LEVEL SECURITY;

-- RNF-27: a customer reads its own orders.
CREATE POLICY orders_customer_select ON orders FOR SELECT TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id());

-- RNF-27: a guest or a customer reads the order of the tracking token it holds (UC-14, the tracking link).
CREATE POLICY orders_tracking_select ON orders FOR SELECT TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND tracking_token_hash = app_tracking_token_hash());

-- RNF-27: at checkout a customer creates its own order, and a guest an order without a user, both awaiting payment
-- and bound to the tracking token of the context.
CREATE POLICY orders_customer_insert ON orders FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'CUSTOMER' AND user_id = app_user_id()
        AND tracking_token_hash = app_tracking_token_hash() AND status = 'AWAITING_PAYMENT');
CREATE POLICY orders_guest_insert ON orders FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'ANONYMOUS' AND user_id IS NULL
        AND tracking_token_hash = app_tracking_token_hash() AND status = 'AWAITING_PAYMENT');

-- RNF-27: the administrator (UC-18) and the system (webhook and expiry, UC-15 and UC-16) read and update every order.
-- What they may change is narrowed by orders_status_update_guard below.
CREATE POLICY orders_staff_select ON orders FOR SELECT TO tienda_app
    USING (app_role() IN ('ADMIN', 'SYSTEM'));
CREATE POLICY orders_staff_update ON orders FOR UPDATE TO tienda_app
    USING (app_role() IN ('ADMIN', 'SYSTEM'))
    WITH CHECK (app_role() IN ('ADMIN', 'SYSTEM'));

-- Policies choose rows, not columns: this trigger keeps the administrator's and the system's updates to the status,
-- the shipment tracking number and the delivery date, so a bug cannot rewrite the buyer, the address or the amounts.
-- Correcting the email (UC-17) is the buyer's, with its own policy (T-E3-23).
CREATE FUNCTION orders_status_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() IN ('ADMIN', 'SYSTEM')
        AND (NEW.id, NEW.number, NEW.user_id, NEW.tracking_token_hash, NEW.customer_name, NEW.customer_email,
             NEW.customer_phone, NEW.national_id, NEW.street, NEW.street_number, NEW.floor, NEW.apartment, NEW.city,
             NEW.province, NEW.postal_code, NEW.subtotal_cents, NEW.discount_cents, NEW.shipping_cents,
             NEW.total_cents, NEW.coupon_id, NEW.coupon_code, NEW.payment_provider, NEW.terms_version,
             NEW.reservation_expires_at, NEW.created_at)
            IS DISTINCT FROM (OLD.id, OLD.number, OLD.user_id, OLD.tracking_token_hash, OLD.customer_name,
             OLD.customer_email, OLD.customer_phone, OLD.national_id, OLD.street, OLD.street_number, OLD.floor,
             OLD.apartment, OLD.city, OLD.province, OLD.postal_code, OLD.subtotal_cents, OLD.discount_cents,
             OLD.shipping_cents, OLD.total_cents, OLD.coupon_id, OLD.coupon_code, OLD.payment_provider,
             OLD.terms_version, OLD.reservation_expires_at, OLD.created_at) THEN
        RAISE EXCEPTION 'the administrator and the system may only update the status, the shipment tracking number and the delivery date of an order'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER orders_status_update_guard BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION orders_status_update_guard();

-- RNF-27: items follow their order. The subquery reads orders under its own policies, so it only finds the orders the
-- actor can see; customers and guests add the lines of the order they are creating, while it awaits payment.
CREATE POLICY order_item_select ON order_item FOR SELECT TO tienda_app
    USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_id));
CREATE POLICY order_item_buyer_insert ON order_item FOR INSERT TO tienda_app
    WITH CHECK (app_role() IN ('CUSTOMER', 'ANONYMOUS')
        AND EXISTS (SELECT 1 FROM orders o WHERE o.id = order_id AND o.status = 'AWAITING_PAYMENT'));
