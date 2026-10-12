-- Payments module (T-E3-01, UC-14, UC-15): one payment per order and provider. Internal data of the orders, so Row
-- Level Security is enabled and forced (RNF-27). order_id refers to the ordering module and has no foreign key
-- (ARCHITECTURE.md section 3, rule 6). The payment is created after the checkout transaction, as SYSTEM, together with
-- the provider's checkout; every notification received goes to payment_event (T-E3-15).

CREATE TABLE payment (
    id                  UUID                     NOT NULL,
    order_id            UUID                     NOT NULL,
    -- MODO arrives with E3b.
    provider            TEXT                     NOT NULL,
    -- The order total, integer cents ARS (R-01), calculated by the server (R-02).
    amount_cents        BIGINT                   NOT NULL,
    -- The status of the last payment verified with the provider (R-06). A rejected payment can be retried until the
    -- reservation expires, so REJECTED may still become APPROVED.
    status              TEXT                     NOT NULL,
    -- The provider's checkout (a MercadoPago preference): null until it is created, outside the checkout transaction.
    -- A retry that finds it null creates it (RNF-20.8).
    checkout_id         TEXT,
    checkout_url        TEXT,
    -- The provider's id of the last verified payment.
    external_payment_id TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT payment_pk PRIMARY KEY (id),
    CONSTRAINT payment_order_provider_uk UNIQUE (order_id, provider),
    CONSTRAINT payment_provider_checkout_uk UNIQUE (provider, checkout_id),
    CONSTRAINT payment_provider_ck CHECK (provider IN ('MERCADOPAGO', 'MODO')),
    CONSTRAINT payment_amount_ck CHECK (amount_cents > 0),
    CONSTRAINT payment_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT payment_checkout_ck CHECK ((checkout_id IS NULL) = (checkout_url IS NULL)),
    CONSTRAINT payment_checkout_id_length_ck CHECK (char_length(checkout_id) BETWEEN 1 AND 200),
    CONSTRAINT payment_checkout_url_ck CHECK (checkout_url LIKE 'https://%' AND char_length(checkout_url) <= 2000),
    CONSTRAINT payment_external_payment_id_length_ck CHECK (char_length(external_payment_id) BETWEEN 1 AND 200),
    CONSTRAINT payment_approved_ck CHECK (status <> 'APPROVED' OR external_payment_id IS NOT NULL)
);

-- Payments are kept with their orders (R-15).
REVOKE DELETE, TRUNCATE ON TABLE payment FROM tienda_app;

ALTER TABLE payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment FORCE ROW LEVEL SECURITY;

-- RNF-27: the administrator reads payments (UC-18).
CREATE POLICY payment_admin_select ON payment FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');

-- RNF-27: the system creates the payment with the provider's checkout and records what the provider verifies.
CREATE POLICY payment_system_select ON payment FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY payment_system_insert ON payment FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY payment_system_update ON payment FOR UPDATE TO tienda_app
    USING (app_role() = 'SYSTEM')
    WITH CHECK (app_role() = 'SYSTEM');
