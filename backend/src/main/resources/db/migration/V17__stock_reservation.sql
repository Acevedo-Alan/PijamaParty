-- Inventory module (T-E3-01, UC-16): stock reserved by the orders awaiting payment. There is no reservation table: an
-- order's RESERVATION, DEDUCTION and RELEASE movements (unique per order, variant and type, RNF-20.4) are the ledger,
-- and orders.reservation_expires_at is the expiry.

-- A reservation is a conditional update (reserved + :n WHERE on_hand - reserved >= :n), a deduction lowers on_hand and
-- reserved together, a release lowers reserved; available stock is never negative (R-03). No order has reserved
-- anything before this migration, so every existing row starts at 0.
ALTER TABLE stock_level
    ADD COLUMN reserved INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT stock_level_reserved_ck CHECK (reserved BETWEEN 0 AND on_hand);

-- Order movements carry the sign of what they do to the stock: a reservation and a deduction take units away, a release
-- gives them back. No order movement has been recorded before this migration.
ALTER TABLE stock_movement
    ADD CONSTRAINT stock_movement_order_sign_ck CHECK (
        (type NOT IN ('RESERVATION', 'DEDUCTION') OR quantity < 0) AND (type <> 'RELEASE' OR quantity > 0));

-- UC-01 to UC-03, UC-08: the stock the catalog and the cart may sell is what is not reserved. Replacing the view keeps
-- its read-only privileges (V9).
CREATE OR REPLACE VIEW v_variant_availability AS
SELECT variant_id, on_hand - reserved AS available
FROM stock_level;
