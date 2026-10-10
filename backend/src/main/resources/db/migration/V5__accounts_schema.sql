-- Accounts module (T-E1-01, UC-04): users. Personal data, so Row Level Security is enabled and forced (RNF-27).
-- In E1 only the administrator exists; customers arrive in E4 (CU-19, CU-27).

CREATE TABLE app_user (
    id                    UUID                     NOT NULL,
    -- Stored normalized (trimmed, lower case) so uniqueness and sign-in ignore case.
    email                 TEXT                     NOT NULL,
    email_verified        BOOLEAN                  NOT NULL,
    -- Null for accounts that only sign in with Google or Facebook (CU-27).
    password_hash         TEXT,
    name                  TEXT                     NOT NULL,
    role                  TEXT                     NOT NULL,
    -- UC-04: 5 consecutive failures lock the account for 15 minutes (rule in the User aggregate).
    failed_login_attempts INTEGER                  NOT NULL,
    locked_until          TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT app_user_pk PRIMARY KEY (id),
    CONSTRAINT app_user_email_uk UNIQUE (email),
    CONSTRAINT app_user_email_normalized_ck CHECK (email = lower(btrim(email)) AND email LIKE '_%@_%'),
    CONSTRAINT app_user_email_length_ck CHECK (char_length(email) <= 254),
    CONSTRAINT app_user_name_length_ck CHECK (name = btrim(name) AND char_length(name) BETWEEN 1 AND 100),
    CONSTRAINT app_user_role_ck CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT app_user_failed_login_attempts_ck CHECK (failed_login_attempts >= 0),
    -- R-14: the administrator signs in with a password only (never with Google or Facebook).
    CONSTRAINT app_user_admin_password_ck CHECK (role <> 'ADMIN' OR password_hash IS NOT NULL)
);

-- UC-04, RNF-24: a single administrator, the owner.
CREATE UNIQUE INDEX app_user_single_admin_uk ON app_user (role) WHERE role = 'ADMIN';

ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_user FORCE ROW LEVEL SECURITY;

-- RNF-27: a customer reads and updates its own user and can never make it an administrator.
CREATE POLICY app_user_customer_select ON app_user FOR SELECT TO tienda_app
    USING (app_role() = 'CUSTOMER' AND id = app_user_id());
CREATE POLICY app_user_customer_update ON app_user FOR UPDATE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND id = app_user_id())
    WITH CHECK (app_role() = 'CUSTOMER' AND id = app_user_id() AND role = 'CUSTOMER');

-- RNF-27: the administrator reads its own user.
CREATE POLICY app_user_admin_select ON app_user FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN' AND id = app_user_id());

-- RNF-27: the system creates the initial administrator, reads users to authenticate and records failed attempts and
-- the lock (sign-in runs before there is a signed-in actor). Creating customers arrives with E4 (CU-19, CU-27).
CREATE POLICY app_user_system_select ON app_user FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY app_user_system_insert ON app_user FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM' AND role = 'ADMIN');
CREATE POLICY app_user_system_update ON app_user FOR UPDATE TO tienda_app
    USING (app_role() = 'SYSTEM')
    WITH CHECK (app_role() = 'SYSTEM');

-- Policies choose rows, not columns: this trigger keeps the system's updates to the sign-in bookkeeping, so a bug on
-- the unauthenticated sign-in path cannot change a password, an email or a role (RNF-27).
CREATE FUNCTION app_user_system_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() = 'SYSTEM'
        AND (NEW.id, NEW.email, NEW.email_verified, NEW.password_hash, NEW.name, NEW.role, NEW.created_at)
            IS DISTINCT FROM (OLD.id, OLD.email, OLD.email_verified, OLD.password_hash, OLD.name, OLD.role,
                              OLD.created_at) THEN
        RAISE EXCEPTION 'the system may only update failed sign-in attempts and the lock of a user'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER app_user_system_update_guard
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION app_user_system_update_guard();
