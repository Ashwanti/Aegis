
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    entity_id       UUID,
    user_id         UUID,
    event_type      VARCHAR(100) NOT NULL,   -- e.g. EXCEPTION_STATUS_CHANGED, TRANSACTION_MATCHED
    resource_type   VARCHAR(100) NOT NULL,   -- e.g. EXCEPTION, TRANSACTION, SOURCE
    resource_id     UUID,
    old_value       JSONB,
    new_value       JSONB,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

