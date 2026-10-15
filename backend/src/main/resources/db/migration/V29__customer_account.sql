-- T-E4-01: the customer account (UC-19 to UC-22). app_user (V5) already has email_verified, the optional password
-- hash and the name; this adds the optional phone, the address book and the single-use tokens of the email links.
-- Personal data, so the new tables have Row Level Security enabled and forced (RNF-27). The national ID is never
-- stored on the account: the checkout asks for it on every purchase (UC-21).

-- UC-21: prefilled in the checkout; digits only, as the order's phone (UC-13).
ALTER TABLE app_user
    ADD COLUMN phone TEXT,
    ADD CONSTRAINT app_user_phone_ck CHECK (phone ~ '^[0-9]{8,15}$');

-- UC-20: a customer's addresses, with the formats of the order's address (V14). An order keeps its own copy (R-04),
-- so deleting an address never touches an order. The limit of 10 belongs to the address book in the domain.
CREATE TABLE address (
    id            UUID                     NOT NULL,
    user_id       UUID                     NOT NULL,
    street        TEXT                     NOT NULL,
    street_number TEXT                     NOT NULL,
    floor         TEXT,
    apartment     TEXT,
    city          TEXT                     NOT NULL,
    province      TEXT                     NOT NULL,
    postal_code   TEXT                     NOT NULL,
    is_primary    BOOLEAN                  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT address_pk PRIMARY KEY (id),
    -- UC-28: the addresses go with the account.
    CONSTRAINT address_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT address_street_ck CHECK (street = btrim(street) AND char_length(street) BETWEEN 1 AND 100),
    CONSTRAINT address_street_number_ck CHECK (
        street_number = btrim(street_number) AND char_length(street_number) BETWEEN 1 AND 10),
    CONSTRAINT address_floor_ck CHECK (floor = btrim(floor) AND char_length(floor) BETWEEN 1 AND 10),
    CONSTRAINT address_apartment_ck CHECK (apartment = btrim(apartment) AND char_length(apartment) BETWEEN 1 AND 10),
    CONSTRAINT address_city_ck CHECK (city = btrim(city) AND char_length(city) BETWEEN 1 AND 80),
    -- UC-12: the 24 jurisdictions of Argentina, as the Province enum.
    CONSTRAINT address_province_ck CHECK (province IN (
        'BUENOS_AIRES', 'CIUDAD_DE_BUENOS_AIRES', 'CATAMARCA', 'CHACO', 'CHUBUT', 'CORDOBA', 'CORRIENTES',
        'ENTRE_RIOS', 'FORMOSA', 'JUJUY', 'LA_PAMPA', 'LA_RIOJA', 'MENDOZA', 'MISIONES', 'NEUQUEN', 'RIO_NEGRO',
        'SALTA', 'SAN_JUAN', 'SAN_LUIS', 'SANTA_CRUZ', 'SANTA_FE', 'SANTIAGO_DEL_ESTERO', 'TIERRA_DEL_FUEGO',
        'TUCUMAN')),
    -- 4 digits, or an Argentine CPA: a letter, 4 digits and 3 letters (S3000ABC).
    CONSTRAINT address_postal_code_ck CHECK (postal_code ~ '^([0-9]{4}|[A-Z][0-9]{4}[A-Z]{3})$')
);

CREATE INDEX address_user_idx ON address (user_id);
-- UC-20: at most one primary address per customer.
CREATE UNIQUE INDEX address_primary_uk ON address (user_id) WHERE is_primary;

ALTER TABLE address ENABLE ROW LEVEL SECURITY;
ALTER TABLE address FORCE ROW LEVEL SECURITY;

-- RNF-27: a customer does everything on its own addresses and can never move one to another account.
CREATE POLICY address_customer_select ON address FOR SELECT TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id());
CREATE POLICY address_customer_insert ON address FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'CUSTOMER' AND user_id = app_user_id());
CREATE POLICY address_customer_update ON address FOR UPDATE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id())
    WITH CHECK (app_role() = 'CUSTOMER' AND user_id = app_user_id());
CREATE POLICY address_customer_delete ON address FOR DELETE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id());

-- RNF-27: the administrator reads addresses.
CREATE POLICY address_admin_select ON address FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');

-- UC-19, UC-27: the single-use tokens of the email links, only as SHA-256 (RNF-29.6). The email verification lasts
-- 24 hours and the password reset 30 minutes; the link confirmation of UC-27 arrives with T-E4-13.
CREATE TABLE user_token (
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    purpose    TEXT                     NOT NULL,
    token_hash TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT user_token_pk PRIMARY KEY (id),
    -- UC-28: the tokens go with the account.
    CONSTRAINT user_token_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT user_token_hash_uk UNIQUE (token_hash),
    CONSTRAINT user_token_hash_format_ck CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT user_token_purpose_ck CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT user_token_expiry_ck CHECK (
        (purpose = 'EMAIL_VERIFICATION' AND expires_at = created_at + interval '24 hours')
        OR (purpose = 'PASSWORD_RESET' AND expires_at = created_at + interval '30 minutes')),
    -- A token is used once, and only before it expires.
    CONSTRAINT user_token_used_ck CHECK (used_at IS NULL OR (used_at >= created_at AND used_at < expires_at))
);

CREATE INDEX user_token_user_idx ON user_token (user_id);
CREATE INDEX user_token_expires_at_idx ON user_token (expires_at);

ALTER TABLE user_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_token FORCE ROW LEVEL SECURITY;

-- RNF-27: only the system, which sends the links and consumes them without a session, reads and writes tokens; it
-- also deletes them in the cleanup. Nobody else ever sees them.
CREATE POLICY user_token_system_select ON user_token FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY user_token_system_insert ON user_token FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY user_token_system_update ON user_token FOR UPDATE TO tienda_app
    USING (app_role() = 'SYSTEM')
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY user_token_system_delete ON user_token FOR DELETE TO tienda_app
    USING (app_role() = 'SYSTEM');

-- Policies choose rows, not columns: a token only changes from unused to used.
CREATE FUNCTION user_token_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF (NEW.id, NEW.user_id, NEW.purpose, NEW.token_hash, NEW.created_at, NEW.expires_at)
            IS DISTINCT FROM (OLD.id, OLD.user_id, OLD.purpose, OLD.token_hash, OLD.created_at, OLD.expires_at)
        OR OLD.used_at IS NOT NULL OR NEW.used_at IS NULL THEN
        RAISE EXCEPTION 'a user token only changes from unused to used'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER user_token_update_guard
    BEFORE UPDATE ON user_token
    FOR EACH ROW EXECUTE FUNCTION user_token_update_guard();

-- The SHA-256 of the account token consumed by the transaction (email verification or password reset), set by the
-- application with the rest of the Row Level Security context; NULL when there is none (V2).
CREATE FUNCTION app_account_token_hash() RETURNS TEXT
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT nullif(current_setting('app.account_token_hash', true), '') $$;

GRANT EXECUTE ON FUNCTION app_account_token_hash() TO tienda_app;

-- Whether the transaction consumed a token of this user and purpose: the token named by the context, already used
-- and not expired when it was used (user_token_used_ck). Read with the caller's rights, so only SYSTEM finds one.
CREATE FUNCTION app_user_token_consumed(token_user_id UUID, token_purpose TEXT) RETURNS BOOLEAN
    LANGUAGE sql STABLE
    AS $$
        SELECT EXISTS (
            SELECT 1 FROM user_token
            WHERE token_hash = app_account_token_hash()
              AND user_id = token_user_id
              AND purpose = token_purpose
              AND used_at IS NOT NULL)
    $$;

GRANT EXECUTE ON FUNCTION app_user_token_consumed(UUID, TEXT) TO tienda_app;

-- UC-19: the system registers customers, unverified and with a password (Google and Facebook arrive with T-E4-13).
DROP POLICY app_user_system_insert ON app_user;
CREATE POLICY app_user_system_insert ON app_user FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM' AND (
        role = 'ADMIN'
        OR (role = 'CUSTOMER' AND NOT email_verified AND password_hash IS NOT NULL AND phone IS NULL
            AND failed_login_attempts = 0 AND locked_until IS NULL)));

-- The update guard of V5 and V11, widened for E4:
-- - SYSTEM keeps the sign-in bookkeeping and the second factor. It may also mark an email verified, or replace a
--   customer's password, but only while consuming a token of that user and purpose in the same transaction: the use
--   case marks the token used first and the context names its hash. A bug on an unauthenticated path cannot verify an
--   email or change a password of any account.
-- - A customer changes only its name, phone and password (UC-27 creates a password; checking the current one is the
--   use case's job): never its email, its verification, its role or its sign-in bookkeeping.
CREATE OR REPLACE FUNCTION app_user_system_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() = 'SYSTEM' THEN
        IF (NEW.id, NEW.email, NEW.name, NEW.phone, NEW.role, NEW.created_at)
                IS DISTINCT FROM (OLD.id, OLD.email, OLD.name, OLD.phone, OLD.role, OLD.created_at)
            OR (NEW.email_verified IS DISTINCT FROM OLD.email_verified
                AND NOT (NOT OLD.email_verified AND NEW.email_verified
                         AND app_user_token_consumed(NEW.id, 'EMAIL_VERIFICATION')))
            OR (NEW.password_hash IS DISTINCT FROM OLD.password_hash
                AND NOT (OLD.role = 'CUSTOMER' AND NEW.password_hash IS NOT NULL
                         AND app_user_token_consumed(NEW.id, 'PASSWORD_RESET'))) THEN
            RAISE EXCEPTION 'the system may only update failed sign-in attempts, the lock and the second factor, '
                            'and verify an email or reset a password with its token'
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
    ELSIF app_role() = 'CUSTOMER' THEN
        IF (NEW.id, NEW.email, NEW.email_verified, NEW.role, NEW.failed_login_attempts, NEW.locked_until,
            NEW.totp_secret, NEW.totp_enabled_at, NEW.totp_last_step, NEW.created_at)
            IS DISTINCT FROM (OLD.id, OLD.email, OLD.email_verified, OLD.role, OLD.failed_login_attempts,
                              OLD.locked_until, OLD.totp_secret, OLD.totp_enabled_at, OLD.totp_last_step,
                              OLD.created_at)
            OR (OLD.password_hash IS NOT NULL AND NEW.password_hash IS NULL) THEN
            RAISE EXCEPTION 'a customer may only update its name, phone and password'
                USING ERRCODE = 'insufficient_privilege';
        END IF;
    END IF;
    RETURN NEW;
END
$$;
