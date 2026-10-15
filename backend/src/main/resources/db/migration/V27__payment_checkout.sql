-- Payments module (T-E3b-02, UC-14, RNF-20.8): every checkout created at a provider for a payment, one row per
-- attempt. A MODO payment request lasts 5 to 10 minutes, so "Pagar" creates a new one when the last expired; each has
-- its own external_intention_id, which MODO does not return when asked, so a checkout id must always lead back to its
-- payment, also for an older attempt. MercadoPago writes its single attempt too. payment.checkout_id and checkout_url
-- stay as the current attempt's.

CREATE TABLE payment_checkout (
    payment_id         UUID                     NOT NULL,
    -- Copied from the payment (payment_checkout_payment_fk keeps it equal), so a checkout id is unique per provider.
    provider           TEXT                     NOT NULL,
    -- 1, 2, ...: max(attempt) + 1 under the payment's row lock.
    attempt            INTEGER                  NOT NULL,
    -- {order number}-{attempt}: MODO's external_intention_id. MercadoPago keeps the order number as its own
    -- external_reference (UC-15 finds its payments by it).
    external_reference TEXT                     NOT NULL,
    -- The provider's checkout: null until the provider answers, and for good when it never did.
    checkout_id        TEXT,
    checkout_url       TEXT,
    -- When the provider stops accepting the checkout: null for MercadoPago, whose checkout lasts as long as the
    -- reservation.
    expires_at         TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT payment_checkout_pk PRIMARY KEY (payment_id, attempt),
    CONSTRAINT payment_checkout_provider_checkout_uk UNIQUE (provider, checkout_id),
    CONSTRAINT payment_checkout_attempt_ck CHECK (attempt > 0),
    CONSTRAINT payment_checkout_external_reference_length_ck CHECK (char_length(external_reference) BETWEEN 1 AND 64),
    CONSTRAINT payment_checkout_checkout_ck CHECK ((checkout_id IS NULL) = (checkout_url IS NULL)),
    CONSTRAINT payment_checkout_expires_ck CHECK (checkout_id IS NOT NULL OR expires_at IS NULL),
    CONSTRAINT payment_checkout_checkout_id_length_ck CHECK (char_length(checkout_id) BETWEEN 1 AND 200),
    CONSTRAINT payment_checkout_checkout_url_ck
        CHECK (checkout_url LIKE 'https://%' AND char_length(checkout_url) <= 2000)
);

-- The foreign key needs the pair to be unique in payment (it is: id is the primary key).
ALTER TABLE payment ADD CONSTRAINT payment_id_provider_uk UNIQUE (id, provider);
ALTER TABLE payment_checkout ADD CONSTRAINT payment_checkout_payment_fk
    FOREIGN KEY (payment_id, provider) REFERENCES payment (id, provider);

-- The existing payments (development only: the store is published in E6) get their single attempt.
INSERT INTO payment_checkout (payment_id, provider, attempt, external_reference, checkout_id, checkout_url, created_at)
SELECT id, provider, 1, order_number || '-1', checkout_id, checkout_url, created_at FROM payment;

-- The attempts are kept with their payments (R-15).
REVOKE DELETE, TRUNCATE ON TABLE payment_checkout FROM tienda_app;

ALTER TABLE payment_checkout ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_checkout FORCE ROW LEVEL SECURITY;

-- RNF-27: the administrator reads the attempts (UC-18).
CREATE POLICY payment_checkout_admin_select ON payment_checkout FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');

-- RNF-27: the system records each attempt before calling the provider and its checkout once the provider answers.
CREATE POLICY payment_checkout_system_select ON payment_checkout FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY payment_checkout_system_insert ON payment_checkout FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY payment_checkout_system_update ON payment_checkout FOR UPDATE TO tienda_app
    USING (app_role() = 'SYSTEM')
    WITH CHECK (app_role() = 'SYSTEM');
