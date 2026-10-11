-- T-E1-07, UC-01 to UC-03: the stock that the catalog's read queries may see (ARCHITECTURE.md section 3, rule 4).
-- Owned by the inventory module; available = on hand − reserved, and reservations arrive in E3, so it is on_hand.
CREATE VIEW v_variant_availability AS
SELECT variant_id, on_hand AS available
FROM stock_level;

-- A simple view is updatable through to stock_level, and the default privileges of deploy/db/init/01-roles.sh grant
-- full DML: the catalog only reads it.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON TABLE v_variant_availability FROM tienda_app;
