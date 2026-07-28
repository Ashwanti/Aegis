CREATE TABLE sources (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    entity_id        UUID           NOT NULL,
    name             VARCHAR(255)   NOT NULL,
    type             VARCHAR(30)    NOT NULL,
    credentials_enc  BYTEA,
    cron             VARCHAR(100),
    parser_bean      VARCHAR(255)   NOT NULL,
    last_ingest_at   TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
 
    CONSTRAINT sources_pkey PRIMARY KEY (id),
    CONSTRAINT sources_entity_id_fkey FOREIGN KEY (entity_id)
        REFERENCES entities (id) ON DELETE RESTRICT,
    CONSTRAINT sources_type_check CHECK (type IN ('BANK', 'GATEWAY'))
);