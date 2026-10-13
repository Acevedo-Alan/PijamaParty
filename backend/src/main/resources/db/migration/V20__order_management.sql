-- T-E3-10, UC-18: the back office manages orders.

-- A payment approved after its order expired or was cancelled (UC-15): the order stays closed, and the back office
-- alerts that it has to be refunded from the provider's panel. Set once, by the payment's transaction.
ALTER TABLE orders
    ADD COLUMN paid_after_closing_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT orders_paid_after_closing_ck CHECK (
        paid_after_closing_at IS NULL OR status IN ('EXPIRED', 'CANCELLED'));

-- The back-office list of every status, newest first (by status it uses orders_status_created_idx, V14).
CREATE INDEX orders_created_idx ON orders (created_at DESC);

-- A cancelled paid order may put its deducted units back: RESTOCK, positive, one per order and variant like the other
-- order movements (stock_movement_order_variant_type_uk, RNF-20.4).
ALTER TABLE stock_movement
    DROP CONSTRAINT stock_movement_type_ck,
    ADD CONSTRAINT stock_movement_type_ck CHECK (
        type IN ('INITIAL_LOAD', 'ADJUSTMENT', 'RESERVATION', 'DEDUCTION', 'RELEASE', 'RESTOCK')),
    DROP CONSTRAINT stock_movement_order_ck,
    ADD CONSTRAINT stock_movement_order_ck CHECK (
        (type IN ('RESERVATION', 'DEDUCTION', 'RELEASE', 'RESTOCK')) = (order_id IS NOT NULL)),
    DROP CONSTRAINT stock_movement_order_sign_ck,
    ADD CONSTRAINT stock_movement_order_sign_ck CHECK (
        (type NOT IN ('RESERVATION', 'DEDUCTION') OR quantity < 0)
        AND (type NOT IN ('RELEASE', 'RESTOCK') OR quantity > 0));

-- RNF-27: orders_status_update_guard (V14) lists the columns the administrator and the system may not change; this
-- column is narrower still: only the system sets it, once, so no update can erase the refund alert.
CREATE FUNCTION orders_paid_after_closing_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() IN ('ADMIN', 'SYSTEM')
        AND NEW.paid_after_closing_at IS DISTINCT FROM OLD.paid_after_closing_at
        AND (app_role() = 'ADMIN' OR OLD.paid_after_closing_at IS NOT NULL) THEN
        RAISE EXCEPTION 'only the system marks an order paid after closing, once'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER orders_paid_after_closing_guard BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION orders_paid_after_closing_guard();
