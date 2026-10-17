-- T-E4-14: sign-in with Google and Facebook (UC-27). An external account whose email the provider does not vouch for
-- (Facebook) is linked only after the customer opens a link confirmation email. Its token is minted when the email is
-- sent, as the other account emails (T-E4-02), so the external account waits here until then. Personal data, so Row
-- Level Security is enabled and forced (RNF-27).

-- One per customer. A new request replaces the one waiting only when the use case allows it (the first request wins
-- for 24 hours, UC-27). The link confirmation token minted from it names the same external account
-- (user_token.provider and subject, V34).
CREATE TABLE pending_link (
    user_id      UUID                     NOT NULL,
    provider     TEXT                     NOT NULL,
    subject      TEXT                     NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pending_link_pk PRIMARY KEY (user_id),
    -- UC-28: it goes with the account.
    CONSTRAINT pending_link_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT pending_link_provider_ck CHECK (provider IN ('GOOGLE', 'FACEBOOK')),
    CONSTRAINT pending_link_subject_ck CHECK (subject ~ '^[!-~]{1,255}$')
);

ALTER TABLE pending_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE pending_link FORCE ROW LEVEL SECURITY;

-- RNF-27: only the system, which handles the provider's return, sends the email and consumes its token. Only
-- customers link external accounts (R-14).
CREATE POLICY pending_link_system_select ON pending_link FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY pending_link_system_insert ON pending_link FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM'
                AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = pending_link.user_id AND u.role = 'CUSTOMER'));
CREATE POLICY pending_link_system_update ON pending_link FOR UPDATE TO tienda_app
    USING (app_role() = 'SYSTEM')
    WITH CHECK (app_role() = 'SYSTEM');
CREATE POLICY pending_link_system_delete ON pending_link FOR DELETE TO tienda_app
    USING (app_role() = 'SYSTEM');

-- The update replaces the external account and the time of the request, never the customer.
CREATE FUNCTION pending_link_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        RAISE EXCEPTION 'a pending link belongs to one customer'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER pending_link_update_guard
    BEFORE UPDATE ON pending_link
    FOR EACH ROW EXECUTE FUNCTION pending_link_update_guard();

-- T-E4-14: the link confirmation email and the link attempt notice (UC-27), the one a customer gets when someone typed
-- its email to connect an external account without email, which links nothing. The reference is the user; each new
-- request is a new version. Only the system enqueues and sends them (V19 policies).
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_type_ck;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_type_ck CHECK (type IN (
    'ORDER_PAID_CUSTOMER', 'ORDER_PAID_ADMIN', 'WITHDRAWAL_RECEIVED_CUSTOMER', 'WITHDRAWAL_RECEIVED_ADMIN',
    'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'LINK_CONFIRMATION', 'LINK_ATTEMPT_NOTICE'));
