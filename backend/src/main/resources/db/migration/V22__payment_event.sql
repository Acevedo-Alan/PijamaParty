-- T-E3-15 (UC-15, RNF-20.3): every verified notification of a provider's payment, recorded once. MercadoPago's
-- notification carries only the payment id, so a notification is one status the provider answered for one of its
-- payments. Written with INSERT ... ON CONFLICT DO NOTHING in the transaction that processes it: only a notification
-- whose row was inserted is processed. Owned by the payments module.

CREATE TABLE payment_event (
    provider            TEXT                     NOT NULL,
    -- The provider's id of the payment.
    external_payment_id TEXT                     NOT NULL,
    -- The status the provider answered when asked (VerifiedStatus).
    status              TEXT                     NOT NULL,
    -- The payment's order number (MercadoPago's external_reference); it may be no order of this store, so no
    -- reference to payment or to the ordering module.
    order_number        BIGINT                   NOT NULL,
    received_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT payment_event_pk PRIMARY KEY (provider, external_payment_id, status),
    CONSTRAINT payment_event_provider_ck CHECK (provider IN ('MERCADOPAGO', 'MODO')),
    CONSTRAINT payment_event_external_payment_id_length_ck CHECK (char_length(external_payment_id) BETWEEN 1 AND 200),
    CONSTRAINT payment_event_status_ck CHECK (status IN ('APPROVED', 'REJECTED', 'PENDING', 'OTHER')),
    CONSTRAINT payment_event_order_number_ck CHECK (order_number > 0)
);

-- The record of what the providers notified is kept with the payments (R-15).
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE payment_event FROM tienda_app;

ALTER TABLE payment_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_event FORCE ROW LEVEL SECURITY;

-- RNF-27: the administrator reads the notifications (UC-18).
CREATE POLICY payment_event_admin_select ON payment_event FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');

-- RNF-27: the system records each notification once. ON CONFLICT needs to read the conflicting row.
CREATE POLICY payment_event_system_select ON payment_event FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY payment_event_system_insert ON payment_event FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');
