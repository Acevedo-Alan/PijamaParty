-- T-E4-13: sign-in with Google and Facebook (UC-27). The external accounts linked to a customer, the single-use link
-- confirmation of an email the provider does not vouch for (Facebook), and the changes of app_user that social sign-in
-- needs. Personal data, so external_identity has Row Level Security enabled and forced (RNF-27). The provider's access
-- tokens are never stored: only its account identifier (the subject).

-- UC-27: a Google or Facebook account linked to a customer, with the email the provider gave when it was linked. A
-- provider account belongs to one customer, and a customer links at most one account of each provider.
CREATE TABLE external_identity (
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    provider   TEXT                     NOT NULL,
    subject    TEXT                     NOT NULL,
    email      TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT external_identity_pk PRIMARY KEY (id),
    -- UC-28: the linked external accounts go with the account.
    CONSTRAINT external_identity_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT external_identity_subject_uk UNIQUE (provider, subject),
    CONSTRAINT external_identity_user_provider_uk UNIQUE (user_id, provider),
    CONSTRAINT external_identity_provider_ck CHECK (provider IN ('GOOGLE', 'FACEBOOK')),
    -- The provider's identifier: printable ASCII without spaces (Google's sub has at most 255 characters).
    CONSTRAINT external_identity_subject_ck CHECK (subject ~ '^[!-~]{1,255}$'),
    -- Normalized as app_user.email, so linking by email compares like with like.
    CONSTRAINT external_identity_email_ck CHECK (
        email = lower(btrim(email)) AND email LIKE '_%@_%' AND char_length(email) <= 254)
);

ALTER TABLE external_identity ENABLE ROW LEVEL SECURITY;
ALTER TABLE external_identity FORCE ROW LEVEL SECURITY;

-- RNF-27: a customer reads and unlinks its own external accounts (whether the last sign-in method may go is the
-- domain's rule, T-E4-15).
CREATE POLICY external_identity_customer_select ON external_identity FOR SELECT TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id());
CREATE POLICY external_identity_customer_delete ON external_identity FOR DELETE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id());

-- RNF-27: the system reads them to authenticate and links them, only to customers: the administrator signs in with a
-- password and a second factor only (R-14). No policy updates a link: it is unlinked and linked again.
CREATE POLICY external_identity_system_select ON external_identity FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY external_identity_system_insert ON external_identity FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM'
                AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = external_identity.user_id AND u.role = 'CUSTOMER'));

-- UC-27: the Facebook link confirmation lasts 24 hours and names the external account it links once it is used.
ALTER TABLE user_token
    ADD COLUMN provider TEXT,
    ADD COLUMN subject  TEXT,
    ADD CONSTRAINT user_token_provider_ck CHECK (provider IN ('GOOGLE', 'FACEBOOK')),
    ADD CONSTRAINT user_token_subject_ck CHECK (subject ~ '^[!-~]{1,255}$'),
    -- Only a link confirmation names an external account, and it always does.
    ADD CONSTRAINT user_token_external_account_ck CHECK (
        (purpose = 'LINK_CONFIRMATION') = (provider IS NOT NULL AND subject IS NOT NULL)
        AND (provider IS NULL) = (subject IS NULL));
ALTER TABLE user_token DROP CONSTRAINT user_token_purpose_ck;
ALTER TABLE user_token ADD CONSTRAINT user_token_purpose_ck CHECK (
    purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'LINK_CONFIRMATION'));
ALTER TABLE user_token DROP CONSTRAINT user_token_expiry_ck;
ALTER TABLE user_token ADD CONSTRAINT user_token_expiry_ck CHECK (
    (purpose IN ('EMAIL_VERIFICATION', 'LINK_CONFIRMATION') AND expires_at = created_at + interval '24 hours')
    OR (purpose = 'PASSWORD_RESET' AND expires_at = created_at + interval '30 minutes'));

-- The guard of V29, with the external account of a link confirmation: a token only changes from unused to used.
CREATE OR REPLACE FUNCTION user_token_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF (NEW.id, NEW.user_id, NEW.purpose, NEW.token_hash, NEW.created_at, NEW.expires_at, NEW.provider, NEW.subject)
            IS DISTINCT FROM (OLD.id, OLD.user_id, OLD.purpose, OLD.token_hash, OLD.created_at, OLD.expires_at,
                              OLD.provider, OLD.subject)
        OR OLD.used_at IS NOT NULL OR NEW.used_at IS NULL THEN
        RAISE EXCEPTION 'a user token only changes from unused to used'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

-- Whether a Google account with this email is linked to this user: Google vouches for the emails it returns
-- (email_verified), Facebook does not. Read with the caller's rights, so only SYSTEM finds one.
CREATE FUNCTION app_user_google_identity_linked(identity_user_id UUID, identity_email TEXT) RETURNS BOOLEAN
    LANGUAGE sql STABLE
    AS $$
        SELECT EXISTS (
            SELECT 1 FROM external_identity
            WHERE user_id = identity_user_id AND provider = 'GOOGLE' AND email = identity_email)
    $$;

GRANT EXECUTE ON FUNCTION app_user_google_identity_linked(UUID, TEXT) TO tienda_app;

-- UC-27: the system also creates customers without a password, who sign in with Google (verified email) or Facebook
-- (unverified until confirmed). Customers with a password are still created unverified (UC-19).
DROP POLICY app_user_system_insert ON app_user;
CREATE POLICY app_user_system_insert ON app_user FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM' AND (
        role = 'ADMIN'
        OR (role = 'CUSTOMER' AND (NOT email_verified OR password_hash IS NULL) AND phone IS NULL
            AND failed_login_attempts = 0 AND locked_until IS NULL)));

-- The update guard of V31, widened for UC-27. SYSTEM may also verify an email when the transaction consumed a link
-- confirmation of that user, or when a Google account with the user's email is linked to it. When that verifies an
-- unverified customer, the same update may drop its password: whoever set it never proved it owned the inbox, so it
-- stops working (a pre-registered account cannot be taken over). Everything else is as in V31.
CREATE OR REPLACE FUNCTION app_user_system_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
DECLARE
    verified_by_link BOOLEAN;
BEGIN
    IF app_role() = 'SYSTEM' THEN
        verified_by_link := NOT OLD.email_verified AND NEW.email_verified AND OLD.role = 'CUSTOMER'
            AND (app_user_token_consumed(NEW.id, 'LINK_CONFIRMATION')
                 OR app_user_google_identity_linked(NEW.id, NEW.email));
        IF (NEW.id, NEW.email, NEW.name, NEW.phone, NEW.role, NEW.created_at)
                IS DISTINCT FROM (OLD.id, OLD.email, OLD.name, OLD.phone, OLD.role, OLD.created_at)
            OR (NEW.email_verified IS DISTINCT FROM OLD.email_verified
                AND NOT (NOT OLD.email_verified AND NEW.email_verified
                         AND (app_user_token_consumed(NEW.id, 'EMAIL_VERIFICATION')
                              OR app_user_token_consumed(NEW.id, 'PASSWORD_RESET')
                              OR verified_by_link)))
            OR (NEW.password_hash IS DISTINCT FROM OLD.password_hash
                AND NOT (OLD.role = 'CUSTOMER' AND NEW.password_hash IS NOT NULL
                         AND app_user_token_consumed(NEW.id, 'PASSWORD_RESET'))
                AND NOT (NEW.password_hash IS NULL AND verified_by_link)) THEN
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
