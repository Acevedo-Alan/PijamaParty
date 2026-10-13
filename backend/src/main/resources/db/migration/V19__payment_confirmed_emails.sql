-- T-E3-09 (UC-17, RNF-23): the payment-confirmed emails. Two modules, each with its own table:
--  * ordering: order_tracking_token, the extra tracking tokens of an order. The order keeps only the hash of its
--    checkout's token (RNF-29.6), so each confirmation email carries a new token, and only its hash is stored here.
--  * notifications: email_outbox, the emails to send, enqueued in the transaction that paid the order.
-- Both have Row Level Security enabled and forced (RNF-27).

CREATE TABLE order_tracking_token (
    -- SHA-256 in lowercase hex of the token (AccessToken.hash()). The token itself is never stored.
    token_hash TEXT                     NOT NULL,
    order_id   UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT order_tracking_token_pk PRIMARY KEY (token_hash),
    CONSTRAINT order_tracking_token_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT order_tracking_token_hash_format_ck CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX order_tracking_token_order_idx ON order_tracking_token (order_id);

-- A link handed out stays valid with its order (R-15).
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE order_tracking_token FROM tienda_app;

ALTER TABLE order_tracking_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_tracking_token FORCE ROW LEVEL SECURITY;

-- RNF-27: the system mints the token of each confirmation email and reads them all.
CREATE POLICY order_tracking_token_system_select ON order_tracking_token FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY order_tracking_token_system_insert ON order_tracking_token FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');

-- RNF-27: a guest or a customer reads only the row of the token it holds, which the orders policy below looks up.
CREATE POLICY order_tracking_token_holder_select ON order_tracking_token FOR SELECT TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND token_hash = app_tracking_token_hash());

-- RNF-27 (UC-14): a guest or a customer reads the order of the tracking token it holds, the checkout's or one of an
-- email (replaces the policy of V14).
DROP POLICY orders_tracking_select ON orders;
CREATE POLICY orders_tracking_select ON orders FOR SELECT TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND (tracking_token_hash = app_tracking_token_hash()
            OR EXISTS (SELECT 1 FROM order_tracking_token t
                       WHERE t.order_id = orders.id AND t.token_hash = app_tracking_token_hash())));

CREATE TABLE email_outbox (
    id                  UUID                     NOT NULL,
    type                TEXT                     NOT NULL,
    -- What the email is about: the order id for the ORDER_PAID types; refers to the ordering module, no foreign key
    -- (ARCHITECTURE.md section 3, rule 6). No personal data here: the recipient and the content are composed when
    -- sending.
    reference           UUID                     NOT NULL,
    -- BOUNCED arrives with T-E3-22.
    status              TEXT                     NOT NULL,
    attempts            INTEGER                  NOT NULL,
    -- A PENDING email is sent at the first job run from this instant on; each attempt moves it (1, 5, 15, 60 min).
    next_attempt_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    -- The provider's id of the sent email, which its bounce notifications carry (T-E3-22).
    provider_message_id TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at             TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT email_outbox_pk PRIMARY KEY (id),
    -- RNF-20.7: each type of email once per order.
    CONSTRAINT email_outbox_type_reference_uk UNIQUE (type, reference),
    CONSTRAINT email_outbox_type_ck CHECK (type IN ('ORDER_PAID_CUSTOMER', 'ORDER_PAID_ADMIN')),
    CONSTRAINT email_outbox_status_ck CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT email_outbox_attempts_ck CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT email_outbox_provider_message_id_ck CHECK (char_length(provider_message_id) BETWEEN 1 AND 200),
    CONSTRAINT email_outbox_sent_ck CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT email_outbox_sent_message_ck CHECK (status <> 'SENT' OR provider_message_id IS NOT NULL)
);

-- The job takes the oldest due email.
CREATE INDEX email_outbox_due_idx ON email_outbox (next_attempt_at) WHERE status = 'PENDING';

-- What was sent is kept, so a repeated event never sends it again.
REVOKE DELETE, TRUNCATE ON TABLE email_outbox FROM tienda_app;

ALTER TABLE email_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_outbox FORCE ROW LEVEL SECURITY;

-- RNF-27: the system enqueues the emails in the payment's transaction and sends them from the job.
CREATE POLICY email_outbox_system_select ON email_outbox FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY email_outbox_system_insert ON email_outbox FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY email_outbox_system_update ON email_outbox FOR UPDATE TO tienda_app
    USING (app_role() = 'SYSTEM')
    WITH CHECK (app_role() = 'SYSTEM');

-- RNF-27: the administrator reads them (the order's email alerts, T-E3-10 and T-E3-22).
CREATE POLICY email_outbox_admin_select ON email_outbox FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');
