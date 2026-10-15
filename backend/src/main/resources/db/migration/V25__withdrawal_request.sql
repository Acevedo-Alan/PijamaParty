-- T-E3-25 (UC-29): the withdrawal button ("botón de arrepentimiento").
--  * ordering: withdrawal_request, one per request, with its procedure code and whether it came out of term.
--  * notifications: email_outbox takes the request's emails, to the buyer and to the administrator.

CREATE TABLE withdrawal_request (
    id          UUID                     NOT NULL,
    order_id    UUID                     NOT NULL,
    -- The procedure code shown to the person and emailed (WithdrawalCode): no characters read alike.
    code        TEXT                     NOT NULL,
    status      TEXT                     NOT NULL,
    -- Recorded, never refused, past the 10-day term (WithdrawalTerm), so the administrator decides.
    out_of_term BOOLEAN                  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT withdrawal_request_pk PRIMARY KEY (id),
    CONSTRAINT withdrawal_request_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT withdrawal_request_code_uk UNIQUE (code),
    CONSTRAINT withdrawal_request_code_ck CHECK (code ~ '^ARR-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$'),
    CONSTRAINT withdrawal_request_status_ck CHECK (status IN ('RECEIVED', 'RESOLVED')),
    CONSTRAINT withdrawal_request_resolved_ck CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL)),
    CONSTRAINT withdrawal_request_resolved_after_ck CHECK (resolved_at >= created_at)
);

-- UC-29: one active request per order; once resolved, the order may have another.
CREATE UNIQUE INDEX withdrawal_request_active_order_uk ON withdrawal_request (order_id) WHERE status = 'RECEIVED';

-- The record of what was requested is kept with the orders (R-15).
REVOKE DELETE, TRUNCATE ON TABLE withdrawal_request FROM tienda_app;

ALTER TABLE withdrawal_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE withdrawal_request FORCE ROW LEVEL SECURITY;

-- RNF-27: the system records the requests, after checking the order's number and email, and reads them to compose
-- their emails. ON CONFLICT needs to read the conflicting row.
CREATE POLICY withdrawal_request_system_select ON withdrawal_request FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY withdrawal_request_system_insert ON withdrawal_request FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM' AND status = 'RECEIVED' AND resolved_at IS NULL);

-- RNF-27: the administrator reads them and resolves them (T-E3-26).
CREATE POLICY withdrawal_request_admin_select ON withdrawal_request FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');
CREATE POLICY withdrawal_request_admin_update ON withdrawal_request FOR UPDATE TO tienda_app
    USING (app_role() = 'ADMIN')
    WITH CHECK (app_role() = 'ADMIN');

-- RNF-20.7: the request's emails, once each; the reference is the request.
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_type_ck;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_type_ck CHECK (type IN (
    'ORDER_PAID_CUSTOMER', 'ORDER_PAID_ADMIN', 'WITHDRAWAL_RECEIVED_CUSTOMER', 'WITHDRAWAL_RECEIVED_ADMIN'));
