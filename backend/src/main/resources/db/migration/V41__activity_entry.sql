-- T-E8-01 (UC-37): the activity log of the back office. One row per change, written in the transaction of the change,
-- never edited; the daily job deletes the rows older than 2 years (D-E8-11). No personal data of buyers or customers:
-- the subject label and the changed values are product and category names, SKUs, coupon codes, order numbers.

CREATE TABLE activity_entry (
    id            UUID                     NOT NULL,
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    -- The administrator who made the change; refers to the accounts module (no foreign key between modules).
    actor_id      UUID                     NOT NULL,
    area          TEXT                     NOT NULL,
    action        TEXT                     NOT NULL,
    subject_type  TEXT                     NOT NULL,
    -- Null for a subject without an id of its own (the shipping table, the store settings).
    subject_id    UUID,
    subject_label TEXT,
    -- An array of {"field", "before", "after"}, before and after as display text or null.
    changes       JSONB                    NOT NULL,
    CONSTRAINT activity_entry_pk PRIMARY KEY (id),
    CONSTRAINT activity_entry_area_ck CHECK (area IN (
        'CATALOG', 'STOCK', 'ORDERS', 'PROMOTIONS', 'REVIEWS', 'SHIPPING', 'CONTENT', 'SETTINGS', 'CUSTOMERS',
        'ACCOUNT')),
    CONSTRAINT activity_entry_action_ck CHECK (action ~ '^[A-Z][A-Z_]{1,59}$'),
    CONSTRAINT activity_entry_subject_type_ck CHECK (subject_type ~ '^[A-Z][A-Z_]{1,39}$'),
    CONSTRAINT activity_entry_subject_label_ck CHECK (char_length(subject_label) BETWEEN 1 AND 200),
    CONSTRAINT activity_entry_changes_ck CHECK (
        jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) <= 20)
);

-- UC-37: the screen lists newest first, every area or one; the retention job deletes by date.
CREATE INDEX activity_entry_occurred_at_idx ON activity_entry (occurred_at DESC, id DESC);
CREATE INDEX activity_entry_area_occurred_at_idx ON activity_entry (area, occurred_at DESC, id DESC);

-- Append-only: nobody edits an entry.
REVOKE UPDATE, TRUNCATE ON TABLE activity_entry FROM tienda_app;

ALTER TABLE activity_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_entry FORCE ROW LEVEL SECURITY;

-- RNF-27: the administrator records its own changes and reads the log.
CREATE POLICY activity_entry_admin_select ON activity_entry FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');
CREATE POLICY activity_entry_admin_insert ON activity_entry FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'ADMIN' AND actor_id = app_user_id());

-- RNF-27: signing in runs as the system, before there is an administrator actor (UC-04), so the system records the
-- ACCOUNT entries only. The retention job reads and deletes the entries older than 2 years, and only those.
CREATE POLICY activity_entry_system_insert ON activity_entry FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM' AND area = 'ACCOUNT');
CREATE POLICY activity_entry_system_select ON activity_entry FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM' AND occurred_at < now() - INTERVAL '2 years');
CREATE POLICY activity_entry_system_delete ON activity_entry FOR DELETE TO tienda_app
    USING (app_role() = 'SYSTEM' AND occurred_at < now() - INTERVAL '2 years');
