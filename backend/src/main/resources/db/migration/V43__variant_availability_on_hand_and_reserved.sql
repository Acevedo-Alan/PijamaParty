-- T-E8-09, UC-07: the "Stock" screen of the back office (module catalog) shows each variant's units on hand, reserved
-- and available, and reads them through the view the inventory publishes (ARCHITECTURE.md section 3, rule 4), never
-- stock_level itself. CREATE OR REPLACE VIEW may only add columns after the existing ones, and keeps the view's
-- read-only privileges (V9): on_hand and reserved are plain columns, so the view would be updatable through them
-- without that REVOKE.
CREATE OR REPLACE VIEW v_variant_availability AS
SELECT variant_id, on_hand - reserved AS available, on_hand, reserved
FROM stock_level;
