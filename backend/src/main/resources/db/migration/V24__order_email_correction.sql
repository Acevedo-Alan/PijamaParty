-- T-E3-23 (UC-17): the buyer corrects the email of its order from the tracking link, and the confirmation is sent
-- again.
--  * ordering: a guest or a customer updates the email of the order of its tracking token, and nothing else.
--  * notifications: email_outbox is versioned, so the confirmation can be enqueued again for the same order; in the
--    correction's transaction the buyer reads, locks and enqueues its own order's confirmations.

-- RNF-27: a guest or a customer updates the order of the tracking token it holds (the expression of
-- orders_tracking_select, V19). orders_buyer_email_update_guard below keeps that update to the email.
CREATE POLICY orders_buyer_email_update ON orders FOR UPDATE TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND (tracking_token_hash = app_tracking_token_hash()
            OR EXISTS (SELECT 1 FROM order_tracking_token t
                       WHERE t.order_id = orders.id AND t.token_hash = app_tracking_token_hash())))
    WITH CHECK (app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND (tracking_token_hash = app_tracking_token_hash()
            OR EXISTS (SELECT 1 FROM order_tracking_token t
                       WHERE t.order_id = orders.id AND t.token_hash = app_tracking_token_hash())));

-- Policies choose rows, not columns: whatever columns orders has now or later, a buyer changes only its email.
CREATE FUNCTION orders_buyer_email_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND to_jsonb(NEW) - 'customer_email' - 'updated_at'
            IS DISTINCT FROM to_jsonb(OLD) - 'customer_email' - 'updated_at' THEN
        RAISE EXCEPTION 'a buyer may only update the email of an order'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER orders_buyer_email_update_guard BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION orders_buyer_email_update_guard();

-- RNF-20.7: each version of an email once per reference. Version 0 is the one the payment enqueues; each correction
-- of the buyer's email may enqueue the next. The unique index also finds the latest version of an email.
ALTER TABLE email_outbox ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_version_ck CHECK (version >= 0);
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_type_reference_uk;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_type_reference_version_uk UNIQUE (type, reference, version);

-- RNF-27: a guest or a customer reaches only the buyer's confirmations of an order it can see. The subquery reads
-- orders under its own policies (the order of its tracking token, or a customer's own), as order_item does.
CREATE POLICY email_outbox_buyer_select ON email_outbox FOR SELECT TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND type = 'ORDER_PAID_CUSTOMER'
        AND EXISTS (SELECT 1 FROM orders o WHERE o.id = reference));

-- RNF-27: it enqueues the confirmation again, as a new version still to send.
CREATE POLICY email_outbox_buyer_insert ON email_outbox FOR INSERT TO tienda_app
    WITH CHECK (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND type = 'ORDER_PAID_CUSTOMER' AND version >= 1
        AND status = 'PENDING' AND attempts = 0 AND provider_message_id IS NULL
        AND EXISTS (SELECT 1 FROM orders o WHERE o.id = reference));

-- RNF-27: it locks a confirmation not yet attempted (SELECT ... FOR UPDATE needs an update policy), so the job cannot
-- compose it before the corrected email commits. It changes nothing: the check refuses every update.
CREATE POLICY email_outbox_buyer_lock ON email_outbox FOR UPDATE TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND type = 'ORDER_PAID_CUSTOMER' AND status = 'PENDING'
        AND attempts = 0 AND EXISTS (SELECT 1 FROM orders o WHERE o.id = reference))
    WITH CHECK (false);
