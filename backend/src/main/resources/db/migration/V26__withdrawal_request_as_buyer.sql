-- T-E3-25 (UC-29, RNF-27): the withdrawal form runs as the guest or the customer that sends it, never as SYSTEM
-- (ArchitectureTest: a customer-facing controller does not see everything). Nobody holds a token or an account for
-- the order, so the database checks what the person proves, the order's number and email, and only then lets the
-- transaction reach that order's requests and their emails.

-- The order the withdrawal form of this transaction matched (lock_withdrawable_order); NULL otherwise.
CREATE FUNCTION app_withdrawal_order_id() RETURNS UUID
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT nullif(current_setting('app.withdrawal_order_id', true), '')::uuid $$;

-- Locks the order of this number when its email is this one (already normalized, as customer_email is stored), and
-- marks it as the transaction's withdrawal order. Owned by tienda_owner (BYPASSRLS), so it finds the order without
-- any policy showing orders by number; it answers nothing more than the person already knows plus what the use case
-- needs. No row for an unknown number or another email, the same for both. The rules of the request stay in Java.
CREATE FUNCTION lock_withdrawable_order(p_number BIGINT, p_email TEXT)
    RETURNS TABLE (order_id UUID, order_number BIGINT, order_status TEXT, order_created_at TIMESTAMP WITH TIME ZONE,
                   order_delivered_at TIMESTAMP WITH TIME ZONE)
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER
    SET search_path = public, pg_temp
    AS $$
DECLARE
    matched orders%ROWTYPE;
BEGIN
    IF app_role() IS DISTINCT FROM 'ANONYMOUS' AND app_role() IS DISTINCT FROM 'CUSTOMER' THEN
        RETURN;
    END IF;
    SELECT * INTO matched FROM orders o
    WHERE o.number = p_number AND o.customer_email = p_email
    FOR UPDATE;
    IF NOT FOUND THEN
        RETURN;
    END IF;
    PERFORM set_config('app.withdrawal_order_id', matched.id::text, true);
    order_id := matched.id;
    order_number := matched.number;
    order_status := matched.status;
    order_created_at := matched.created_at;
    order_delivered_at := matched.delivered_at;
    RETURN NEXT;
END
$$;

GRANT EXECUTE ON FUNCTION app_withdrawal_order_id(), lock_withdrawable_order(BIGINT, TEXT) TO tienda_app;

-- RNF-27: the request is the buyer's to create now, no longer the system's; the system still reads them, to compose
-- their emails.
DROP POLICY withdrawal_request_system_insert ON withdrawal_request;

-- RNF-27: a guest or a customer reads and creates only the requests of the order it matched in this transaction.
CREATE POLICY withdrawal_request_buyer_select ON withdrawal_request FOR SELECT TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND order_id = app_withdrawal_order_id());
CREATE POLICY withdrawal_request_buyer_insert ON withdrawal_request FOR INSERT TO tienda_app
    WITH CHECK (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND order_id = app_withdrawal_order_id()
        AND status = 'RECEIVED' AND resolved_at IS NULL);

-- RNF-27: it enqueues the two emails of such a request, still to send, in the request's transaction. The subquery
-- reads withdrawal_request under the policy above. No personal data in the outbox.
CREATE POLICY email_outbox_withdrawal_select ON email_outbox FOR SELECT TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND type IN ('WITHDRAWAL_RECEIVED_CUSTOMER', 'WITHDRAWAL_RECEIVED_ADMIN')
        AND EXISTS (SELECT 1 FROM withdrawal_request w WHERE w.id = reference));
CREATE POLICY email_outbox_withdrawal_insert ON email_outbox FOR INSERT TO tienda_app
    WITH CHECK (app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND type IN ('WITHDRAWAL_RECEIVED_CUSTOMER', 'WITHDRAWAL_RECEIVED_ADMIN')
        AND version = 0 AND status = 'PENDING' AND attempts = 0 AND provider_message_id IS NULL
        AND sent_at IS NULL
        AND EXISTS (SELECT 1 FROM withdrawal_request w WHERE w.id = reference));
