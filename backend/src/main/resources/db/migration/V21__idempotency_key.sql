-- T-E3-14 (RNF-20.1, RNF-20.2): the Idempotency-Key of every checkout that placed an order, with a hash of its request
-- and the answer it got, so a repetition gets the same order without repeating any effect. Owned by the platform
-- module. Written in the checkout's transaction, with the order: a visible key always has its answer, and a first
-- request still in flight is told apart by a transaction-level advisory lock on the scope and key.

CREATE TABLE idempotency_key (
    -- Whose key: 'USER:' and a customer's id, or 'CART:' and the SHA-256 of a guest's cart token (RNF-29.6).
    scope           TEXT                     NOT NULL,
    idempotency_key UUID                     NOT NULL,
    -- SHA-256 in lowercase hex of the request's content in a canonical encoding.
    request_hash    TEXT                     NOT NULL,
    -- What the first request created (the checkout's order); no foreign key: the platform knows no module's tables.
    resource_id     UUID                     NOT NULL,
    response_status SMALLINT                 NOT NULL,
    -- The answer without secrets: a tracking token is never stored in clear (RNF-29.6).
    response_body   JSONB                    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT idempotency_key_pk PRIMARY KEY (scope, idempotency_key),
    CONSTRAINT idempotency_key_scope_ck CHECK (
        scope ~ '^USER:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        OR scope ~ '^CART:[0-9a-f]{64}$'),
    CONSTRAINT idempotency_key_request_hash_ck CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT idempotency_key_response_status_ck CHECK (response_status BETWEEN 200 AND 299),
    CONSTRAINT idempotency_key_response_body_ck CHECK (jsonb_typeof(response_body) = 'object'),
    CONSTRAINT idempotency_key_expiry_ck CHECK (expires_at > created_at)
);

-- The daily purge (RNF-20.2) finds the expired keys.
CREATE INDEX idempotency_key_expires_at_idx ON idempotency_key (expires_at);

-- A stored answer never changes; only the purge deletes.
REVOKE UPDATE, TRUNCATE ON TABLE idempotency_key FROM tienda_app;

ALTER TABLE idempotency_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_key FORCE ROW LEVEL SECURITY;

-- RNF-27: a customer reads and creates the keys of its own user, never a guest's.
CREATE POLICY idempotency_key_customer_select ON idempotency_key FOR SELECT TO tienda_app
    USING (app_role() = 'CUSTOMER' AND scope = 'USER:' || app_user_id()::text);
CREATE POLICY idempotency_key_customer_insert ON idempotency_key FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'CUSTOMER' AND scope = 'USER:' || app_user_id()::text);

-- RNF-27: a guest reads and creates the keys of its cart token, never a customer's.
CREATE POLICY idempotency_key_guest_select ON idempotency_key FOR SELECT TO tienda_app
    USING (app_role() = 'ANONYMOUS' AND scope = 'CART:' || app_cart_token_hash());
CREATE POLICY idempotency_key_guest_insert ON idempotency_key FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'ANONYMOUS' AND scope = 'CART:' || app_cart_token_hash());

-- RNF-27: the system only finds and deletes the expired keys (the purge).
CREATE POLICY idempotency_key_system_select ON idempotency_key FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY idempotency_key_system_delete ON idempotency_key FOR DELETE TO tienda_app
    USING (app_role() = 'SYSTEM');

-- RNF-27, RNF-29.6 (UC-14): a repeated checkout gets a new tracking token of the order it placed the first time. A
-- guest or a customer adds the token it holds only to the order of one of its own keys: the subquery reads
-- idempotency_key under the policies above.
CREATE POLICY order_tracking_token_repeat_insert ON order_tracking_token FOR INSERT TO tienda_app
    WITH CHECK (app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND token_hash = app_tracking_token_hash()
        AND EXISTS (SELECT 1 FROM idempotency_key k WHERE k.resource_id = order_tracking_token.order_id));
