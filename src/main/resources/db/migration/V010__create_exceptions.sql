-- V010__create_exceptions.sql

CREATE TABLE exceptions (
  id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  run_id           UUID NOT NULL REFERENCES recon_runs(id),
  txn_id           UUID NOT NULL REFERENCES transactions(id),
  type             TEXT NOT NULL,
  status           TEXT NOT NULL DEFAULT 'OPEN',
  priority         TEXT NOT NULL DEFAULT 'MEDIUM',
  assigned_to      UUID REFERENCES users(id),
  amount           NUMERIC(18,6) NOT NULL,
  currency         CHAR(3) NOT NULL,
  resolution_type  TEXT,
  resolution_note  TEXT,
  resolved_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at       TIMESTAMPTZ
);