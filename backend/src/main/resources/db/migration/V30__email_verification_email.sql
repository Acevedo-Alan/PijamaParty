-- T-E4-02: the email that verifies a new customer's address (UC-19). The reference is the user; each new link asked
-- for (a sign-in before verifying) is a new version. Only the system enqueues and sends it (V19 policies).
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_type_ck;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_type_ck CHECK (type IN (
    'ORDER_PAID_CUSTOMER', 'ORDER_PAID_ADMIN', 'WITHDRAWAL_RECEIVED_CUSTOMER', 'WITHDRAWAL_RECEIVED_ADMIN',
    'EMAIL_VERIFICATION'));
