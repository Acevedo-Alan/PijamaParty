-- T-E1-06, UC-07: adjustments are idempotent (R-09): the Idempotency-Key of an administrator's adjustment is unique per
-- variant, so a retry after a lost response never changes the stock twice. The adjustment locks the stock level row
-- and checks the key first; the unique constraint is the last line of defense. No adjustment has been recorded before
-- this migration, so every existing movement satisfies the new checks.
ALTER TABLE stock_movement
    ADD COLUMN adjustment_key UUID,
    ADD CONSTRAINT stock_movement_adjustment_key_ck CHECK (
        (type IN ('INITIAL_LOAD', 'ADJUSTMENT')) = (adjustment_key IS NOT NULL)),
    ADD CONSTRAINT stock_movement_adjustment_key_uk UNIQUE (variant_id, adjustment_key);

-- UC-07: stock is at most 1,000,000 units, which also keeps on_hand far from the integer limit.
ALTER TABLE stock_level
    ADD CONSTRAINT stock_level_on_hand_max_ck CHECK (on_hand <= 1000000);
