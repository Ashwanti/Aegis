
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id           UUID NOT NULL
                            REFERENCES sources (id),
 
    external_id         VARCHAR(255) NOT NULL,          -- source system's own row/txn identifier
    amount              NUMERIC(18,6) NOT NULL,          -- NEVER float/double — money precision
    currency            CHAR(3) NOT NULL,                 -- ISO 4217, e.g. 'INR', 'USD'
    normalized_amount   NUMERIC(18,6),                    -- amount / fx_rate, populated on multi-currency orgs (Phase 4)
 
    direction           VARCHAR(10) NOT NULL
                            CHECK (direction IN ('CREDIT', 'DEBIT')),
 
    txn_date            DATE NOT NULL,                    -- transaction/booking date
    value_date          DATE,                             -- settlement/value date, may differ from txn_date
 
    description         VARCHAR(500),
    reference            VARCHAR(255),                    -- UTR / ARN / free-text ref, used by ReferenceMatchRule + FuzzyReferenceRule
 
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'MATCHED', 'PARTIAL', 'UNMATCHED')),
 
    metadata             JSONB,                            -- raw row / extra source-specific fields
 
    ingested_at          TIMESTAMPTZ NOT NULL DEFAULT now(), -- when this row landed in the DB
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,                      -- soft delete (@SQLDelete + @Where in JPA)
 
    CONSTRAINT uq_transactions_source_external UNIQUE (source_id, external_id)
);