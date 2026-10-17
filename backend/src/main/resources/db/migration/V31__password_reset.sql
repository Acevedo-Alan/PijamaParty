-- T-E4-03: the password recovery of a customer (UC-19). The reset email's reference is the user; each new link asked
-- for is a new version. Only the system enqueues and sends it (V19 policies).
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_type_ck;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_type_ck CHECK (type IN (
    'ORDER_PAID_CUSTOMER', 'ORDER_PAID_ADMIN', 'WITHDRAWAL_RECEIVED_CUSTOMER', 'WITHDRAWAL_RECEIVED_ADMIN',
    'EMAIL_VERIFICATION', 'PASSWORD_RESET'));

-- The update guard of V29, where a used password reset token may also verify the email: opening the reset link
-- proves the customer owns the inbox, as the verification link does. Everything else is as in V29.
CREATE OR REPLACE FUNCTION app_user_system_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() = 'SYSTEM' THEN
        IF (NEW.id, NEW.email, NEW.name, NEW.phone, NEW.role, NEW.created_at)
                IS DISTINCT FROM (OLD.id, OLD.email, OLD.name, OLD.phone, OLD.role, OLD.created_at)
            OR (NEW.email_verified IS DISTINCT FROM OLD.email_verified
                AND NOT (NOT OLD.email_verified AND NEW.email_verified
                         AND (app_user_token_consumed(NEW.id, 'EMAIL_VERIFICATION')
                              OR app_user_token_consumed(NEW.id, 'PASSWORD_RESET'))))
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
