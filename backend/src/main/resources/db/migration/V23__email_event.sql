-- T-E3-22 (UC-17, UC-18, RNF-23): the bounces and complaints the email provider notifies. Owned by the notifications
-- module:
--  * email_event, every notification recorded once by its provider's event id (RNF-20.3), processed only when its
--    row was inserted.
--  * email_outbox gains BOUNCED: a sent email the provider reports as never delivered, for the back-office alert.

CREATE TABLE email_event (
    provider            TEXT                     NOT NULL,
    -- The provider's id of the notification (Resend's svix-id), the same on every retry of it.
    event_id            TEXT                     NOT NULL,
    -- EmailEventType: what the notification says of the email.
    type                TEXT                     NOT NULL,
    -- The provider's id of the email, as email_outbox.provider_message_id. No recipient, sender or subject (R-05).
    provider_message_id TEXT                     NOT NULL,
    received_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT email_event_pk PRIMARY KEY (provider, event_id),
    CONSTRAINT email_event_provider_ck CHECK (provider IN ('RESEND')),
    CONSTRAINT email_event_event_id_length_ck CHECK (char_length(event_id) BETWEEN 1 AND 200),
    CONSTRAINT email_event_type_ck
        CHECK (type IN ('PERMANENT_BOUNCE', 'TRANSIENT_BOUNCE', 'SUPPRESSED', 'COMPLAINT')),
    CONSTRAINT email_event_provider_message_id_length_ck CHECK (char_length(provider_message_id) BETWEEN 1 AND 200)
);

-- What the provider notified is kept (R-15).
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE email_event FROM tienda_app;

ALTER TABLE email_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_event FORCE ROW LEVEL SECURITY;

-- RNF-27: the administrator reads the notifications (UC-18).
CREATE POLICY email_event_admin_select ON email_event FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');

-- RNF-27: the system records each notification once. ON CONFLICT needs to read the conflicting row.
CREATE POLICY email_event_system_select ON email_event FOR SELECT TO tienda_app
    USING (app_role() = 'SYSTEM');
CREATE POLICY email_event_system_insert ON email_event FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'SYSTEM');

-- A bounced email was sent: it keeps when and with which provider's id.
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_status_ck;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_status_ck
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'BOUNCED'));
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_sent_ck;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_sent_ck
    CHECK ((status IN ('SENT', 'BOUNCED')) = (sent_at IS NOT NULL));
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_sent_message_ck;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_sent_message_ck
    CHECK (status NOT IN ('SENT', 'BOUNCED') OR provider_message_id IS NOT NULL);

-- A bounce finds its email by the provider's id.
CREATE INDEX email_outbox_provider_message_idx ON email_outbox (provider_message_id)
    WHERE provider_message_id IS NOT NULL;
