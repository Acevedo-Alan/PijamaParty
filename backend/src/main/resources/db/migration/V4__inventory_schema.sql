-- Inventory module (T-E1-01, UC-07). No Row Level Security: the stock tables are outside the RNF-27 scope list.
-- variant_id refers to the catalog module and has no foreign key (ARCHITECTURE.md section 3, rule 6): the
-- stock_level row is created when catalog publishes VariantCreated.

CREATE TABLE stock_level (
    variant_id UUID                     NOT NULL,
    on_hand    INTEGER                  NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stock_level_pk PRIMARY KEY (variant_id),
    -- UC-07: stock never goes negative. Adjustments are conditional updates (WHERE on_hand + :delta >= 0); this is
    -- the last line of defense.
    CONSTRAINT stock_level_on_hand_ck CHECK (on_hand >= 0)
);

CREATE TABLE stock_movement (
    id         UUID                     NOT NULL,
    variant_id UUID                     NOT NULL,
    type       TEXT                     NOT NULL,
    -- Signed: positive adds stock, negative removes it.
    quantity   INTEGER                  NOT NULL,
    -- Optional free text from the administrator, shown as the reason in the history (UC-07).
    note       TEXT,
    -- The order behind RESERVATION, DEDUCTION and RELEASE (E3); refers to the ordering module, no foreign key.
    order_id   UUID,
    -- The administrator behind INITIAL_LOAD and ADJUSTMENT; refers to the accounts module, no foreign key.
    user_id    UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stock_movement_pk PRIMARY KEY (id),
    CONSTRAINT stock_movement_stock_level_fk FOREIGN KEY (variant_id) REFERENCES stock_level (variant_id),
    CONSTRAINT stock_movement_type_ck CHECK (
        type IN ('INITIAL_LOAD', 'ADJUSTMENT', 'RESERVATION', 'DEDUCTION', 'RELEASE')),
    CONSTRAINT stock_movement_quantity_ck CHECK (quantity <> 0),
    CONSTRAINT stock_movement_note_length_ck CHECK (char_length(note) <= 200),
    CONSTRAINT stock_movement_order_ck CHECK (
        (type IN ('RESERVATION', 'DEDUCTION', 'RELEASE')) = (order_id IS NOT NULL)),
    CONSTRAINT stock_movement_user_ck CHECK (type NOT IN ('INITIAL_LOAD', 'ADJUSTMENT') OR user_id IS NOT NULL)
);

-- RNF-20.4: one movement of each order type per order and variant, so a repeated reservation, deduction or release
-- is a no-op (INSERT ... ON CONFLICT DO NOTHING).
CREATE UNIQUE INDEX stock_movement_order_variant_type_uk ON stock_movement (order_id, variant_id, type)
    WHERE order_id IS NOT NULL;

-- UC-07: the history of a variant, newest first.
CREATE INDEX stock_movement_variant_created_idx ON stock_movement (variant_id, created_at DESC);

-- UC-07: the history is append-only. The default privileges of deploy/db/init/01-roles.sh grant full DML; take
-- back what an append-only table must not allow.
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE stock_movement FROM tienda_app;
