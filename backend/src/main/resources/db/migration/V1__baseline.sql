-- Baseline: infrastructure tables owned by libraries. Business tables arrive with the stage that creates them.

-- Spring Modulith event publication registry (ARCHITECTURE.md section 6): domain events are stored here and
-- delivered at least once, so every listener is idempotent. Mapped by spring-modulith-events-jpa.
CREATE TABLE event_publication (
    id               UUID                     NOT NULL,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT event_publication_pk PRIMARY KEY (id)
);

CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Used only if the completion mode is switched to ARCHIVE; created so the JPA mapping validates.
CREATE TABLE event_publication_archive (
    id               UUID                     NOT NULL,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT event_publication_archive_pk PRIMARY KEY (id)
);

-- Spring Session JDBC (RNF-29.1), schema-postgresql.sql of spring-session-jdbc 3.5.
CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT   NOT NULL,
    last_access_time      BIGINT   NOT NULL,
    max_inactive_interval INT      NOT NULL,
    expiry_time           BIGINT   NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
