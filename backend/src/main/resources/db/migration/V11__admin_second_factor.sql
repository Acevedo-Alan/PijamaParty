-- T-E1-16, UC-04: the administrator's second factor (TOTP) and its recovery codes.

-- The authenticator secret, encrypted by the application (AES-256-GCM, key outside the database); when it was
-- enrolled; and the time step of the last accepted code, so a code is never accepted twice.
ALTER TABLE app_user
    ADD COLUMN totp_secret     BYTEA,
    ADD COLUMN totp_enabled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN totp_last_step  BIGINT,
    ADD CONSTRAINT app_user_totp_complete_ck CHECK (
        (totp_secret IS NULL AND totp_enabled_at IS NULL AND totp_last_step IS NULL)
        OR (totp_secret IS NOT NULL AND totp_enabled_at IS NOT NULL AND totp_last_step >= 0)),
    ADD CONSTRAINT app_user_totp_admin_ck CHECK (totp_secret IS NULL OR role = 'ADMIN');

-- One-time recovery codes, only as SHA-256. Deleted with the user.
CREATE TABLE admin_recovery_code (
    user_id    UUID                     NOT NULL,
    code_hash  BYTEA                    NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT admin_recovery_code_pk PRIMARY KEY (user_id, code_hash),
    CONSTRAINT admin_recovery_code_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT admin_recovery_code_hash_length_ck CHECK (octet_length(code_hash) = 32)
);

-- RNF-27: only the sign-in, which runs as SYSTEM, reads and writes them; nobody else ever sees them.
ALTER TABLE admin_recovery_code ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_recovery_code FORCE ROW LEVEL SECURITY;

CREATE POLICY admin_recovery_code_system_select ON admin_recovery_code FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY admin_recovery_code_system_insert ON admin_recovery_code FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY admin_recovery_code_system_update ON admin_recovery_code FOR UPDATE TO tienda_app
    USING (app_role() = 'SYSTEM')
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY admin_recovery_code_system_delete ON admin_recovery_code FOR DELETE TO tienda_app
    USING (app_role() = 'SYSTEM');

-- The guard of V5 keeps SYSTEM's updates to the sign-in bookkeeping. With the second factor, SYSTEM may also store
-- one when there is none, move the last accepted step forward, or delete the second factor (reset), and nothing else.
CREATE OR REPLACE FUNCTION app_user_system_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() = 'SYSTEM' THEN
        IF (NEW.id, NEW.email, NEW.email_verified, NEW.password_hash, NEW.name, NEW.role, NEW.created_at)
            IS DISTINCT FROM (OLD.id, OLD.email, OLD.email_verified, OLD.password_hash, OLD.name, OLD.role,
                              OLD.created_at) THEN
            RAISE EXCEPTION 'the system may only update failed sign-in attempts, the lock and the second factor'
                USING ERRCODE = 'insufficient_privilege';
        END IF;
        IF (NEW.totp_secret, NEW.totp_enabled_at) IS DISTINCT FROM (OLD.totp_secret, OLD.totp_enabled_at)
            AND OLD.totp_secret IS NOT NULL AND NEW.totp_secret IS NOT NULL THEN
            RAISE EXCEPTION 'a second factor is replaced only by deleting it first'
                USING ERRCODE = 'insufficient_privilege';
        END IF;
        IF OLD.totp_last_step IS NOT NULL AND NEW.totp_last_step IS NOT NULL
            AND NEW.totp_secret IS NOT DISTINCT FROM OLD.totp_secret
            AND NEW.totp_last_step < OLD.totp_last_step THEN
            RAISE EXCEPTION 'the last accepted step only moves forward'
                USING ERRCODE = 'insufficient_privilege';
        END IF;
    END IF;
    RETURN NEW;
END
$$;
